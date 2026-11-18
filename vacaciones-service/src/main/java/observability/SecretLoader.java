package observability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

public final class SecretLoader {
    private SecretLoader() {
    }

    public static void load(String secretName, String environmentVariable) {
        load(secretName, environmentVariable, System::getenv);
    }

    static void load(String secretName, String environmentVariable,
            Function<String, String> environmentLookup) {
        String value = readSecretFile(secretName);
        if (value == null) {
            value = environmentLookup.apply(environmentVariable);
        }
        validate(secretName, environmentVariable, value);
        System.setProperty(environmentVariable, value.strip());
    }

    private static void validate(String secretName, String environmentVariable, String value) {
        String hint = " Montar /run/secrets/" + secretName + " o definir "
                + environmentVariable + ".";
        if (value == null) {
            throw new IllegalStateException("ERROR: Secreto '" + secretName
                    + "' no configurado." + hint);
        }
        if (value.isBlank()) {
            throw new IllegalStateException("ERROR: Secreto '" + secretName
                    + "' está vacío." + hint);
        }
        if (value.startsWith("REEMPLAZAR") || value.startsWith("GESTIONADO_EN_SECRETS")) {
            throw new IllegalStateException("ERROR: Secreto '" + secretName
                    + "' contiene un marcador de plantilla inseguro." + hint);
        }
    }

    private static String readSecretFile(String secretName) {
        String directory = System.getProperty("secrets.dir",
                System.getenv().getOrDefault("SECRETS_DIR", "/run/secrets"));
        Path path = Path.of(directory, secretName);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path).strip();
        } catch (IOException exception) {
            throw new IllegalStateException("ERROR: No se pudo leer el secreto '" + secretName
                    + "' desde " + path + ".", exception);
        }
    }
}
