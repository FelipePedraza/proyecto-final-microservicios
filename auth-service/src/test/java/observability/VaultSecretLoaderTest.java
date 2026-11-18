package observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class VaultSecretLoaderTest {
    private static final String CLIENT_TOKEN = "token-limitado-de-prueba";

    private final Map<String, String> vaultValues = new ConcurrentHashMap<>();
    private final AtomicReference<String> loginBody = new AtomicReference<>();
    private final AtomicReference<String> receivedToken = new AtomicReference<>();
    private HttpServer server;
    private String address;

    @BeforeEach
    void setUp() throws IOException {
        vaultValues.put("/v1/secret/data/auth-service/db-password", "db-seguro");
        vaultValues.put("/v1/secret/data/auth-service/broker-password", "broker-seguro");
        vaultValues.put("/v1/secret/data/auth-service/jwt-secret",
                "jwt-seguro-de-prueba-con-mas-de-treinta-y-dos-bytes");
        vaultValues.put("/v1/secret/data/auth-service/admin-password", "admin-seguro");

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/auth/approle/login", this::handleLogin);
        server.createContext("/v1/secret/data/", this::handleSecretRead);
        server.start();
        address = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        System.clearProperty("DB_PASSWORD");
        System.clearProperty("RABBITMQ_PASSWORD");
        System.clearProperty("JWT_SECRET");
        System.clearProperty("ADMIN_PASSWORD");
    }

    @Test
    void authenticatesWithAppRoleAndLoadsAllAuthSecrets() {
        VaultSecretLoader.loadFromVault(address, "role-id-prueba", "secret-id-prueba");

        assertTrue(loginBody.get().contains("role-id-prueba"));
        assertTrue(loginBody.get().contains("secret-id-prueba"));
        assertEquals(CLIENT_TOKEN, receivedToken.get());
        assertEquals("db-seguro", System.getProperty("DB_PASSWORD"));
        assertEquals("broker-seguro", System.getProperty("RABBITMQ_PASSWORD"));
        assertEquals("jwt-seguro-de-prueba-con-mas-de-treinta-y-dos-bytes",
                System.getProperty("JWT_SECRET"));
        assertEquals("admin-seguro", System.getProperty("ADMIN_PASSWORD"));
    }

    @Test
    void rejectsTemplateValueReturnedByVault() {
        vaultValues.put("/v1/secret/data/auth-service/jwt-secret",
                "REEMPLAZAR_CON_VALOR_REAL");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> VaultSecretLoader.loadFromVault(
                        address, "role-id-prueba", "secret-id-prueba"));

        assertTrue(exception.getMessage().contains("marcador de plantilla inseguro"));
    }

    @Test
    void doesNotIncludeVaultResponseBodyInErrors() throws IOException {
        server.removeContext("/v1/auth/approle/login");
        server.createContext("/v1/auth/approle/login", exchange ->
                respond(exchange, 403, "{\"errors\":[\"valor-que-no-debe-filtrarse\"]}"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> VaultSecretLoader.loadFromVault(
                        address, "role-id-prueba", "secret-id-prueba"));

        assertTrue(exception.getMessage().contains("HTTP 403"));
        assertFalse(exception.getMessage().contains("valor-que-no-debe-filtrarse"));
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        loginBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 200,
                "{\"auth\":{\"client_token\":\"" + CLIENT_TOKEN + "\"}}");
    }

    private void handleSecretRead(HttpExchange exchange) throws IOException {
        receivedToken.set(exchange.getRequestHeaders().getFirst("X-Vault-Token"));
        String value = vaultValues.get(exchange.getRequestURI().getPath());
        if (value == null) {
            respond(exchange, 404, "{\"errors\":[\"not found\"]}");
            return;
        }
        respond(exchange, 200,
                "{\"data\":{\"data\":{\"value\":\"" + value + "\"}}}");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
