package observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretLoaderTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void cleanProperties() {
        System.clearProperty("secrets.dir");
        System.clearProperty("TEST_SECRET_VALUE");
    }

    @Test
    void readsSecretFromFile() throws Exception {
        Files.writeString(tempDir.resolve("test_secret"), "valor-seguro\n");
        System.setProperty("secrets.dir", tempDir.toString());

        SecretLoader.load("test_secret", "TEST_SECRET_FALLBACK_UNUSED");

        assertEquals("valor-seguro", System.getProperty("TEST_SECRET_FALLBACK_UNUSED"));
        System.clearProperty("TEST_SECRET_FALLBACK_UNUSED");
    }

    @Test
    void rejectsTemplateMarker() throws Exception {
        Files.writeString(tempDir.resolve("test_secret"), "REEMPLAZAR_CON_VALOR_REAL\n");
        System.setProperty("secrets.dir", tempDir.toString());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> SecretLoader.load("test_secret", "TEST_SECRET_VALUE"));
        assertEquals("ERROR: Secreto 'test_secret' contiene un marcador de plantilla inseguro. "
                + "Montar /run/secrets/test_secret o definir TEST_SECRET_VALUE.", exception.getMessage());
    }

    @Test
    void fallsBackToEnvironmentWhenFileIsMissing() {
        System.setProperty("secrets.dir", tempDir.toString());

        SecretLoader.load("test_secret", "TEST_SECRET_VALUE", key -> "fallback-seguro");

        assertEquals("fallback-seguro", System.getProperty("TEST_SECRET_VALUE"));
    }

    @Test
    void reportsMissingSecretExplicitly() {
        System.setProperty("secrets.dir", tempDir.toString());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> SecretLoader.load("test_secret", "TEST_SECRET_VALUE", key -> null));

        assertEquals("ERROR: Secreto 'test_secret' no configurado. "
                + "Montar /run/secrets/test_secret o definir TEST_SECRET_VALUE.", exception.getMessage());
    }

    @Test
    void reportsEmptySecretExplicitly() throws Exception {
        Files.writeString(tempDir.resolve("test_secret"), "  \n");
        System.setProperty("secrets.dir", tempDir.toString());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> SecretLoader.load("test_secret", "TEST_SECRET_VALUE"));

        assertEquals("ERROR: Secreto 'test_secret' está vacío. "
                + "Montar /run/secrets/test_secret o definir TEST_SECRET_VALUE.", exception.getMessage());
    }
}
