package observability;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class VaultSecretLoader {
    private static final Map<String, SecretTarget> SECRET_TARGETS = secretTargets();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private VaultSecretLoader() {
    }

    public static boolean loadIfConfigured() {
        String address = System.getenv("VAULT_ADDR");
        if (address == null || address.isBlank()) {
            return false;
        }
        address = normalizeAddress(address);

        int timeoutSeconds = parseTimeout(System.getenv("VAULT_STARTUP_TIMEOUT_SECONDS"));
        Path approleDirectory = Path.of(System.getenv().getOrDefault(
                "VAULT_APPROLE_DIR", "/vault/approle"));
        long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();
        IllegalStateException lastFailure = null;

        while (System.nanoTime() < deadline) {
            try {
                String roleId = readCredential(approleDirectory.resolve("role_id"), "RoleID");
                String secretId = readCredential(approleDirectory.resolve("secret_id"), "SecretID");
                loadFromVault(address, roleId, secretId);
                return true;
            } catch (IllegalStateException exception) {
                lastFailure = exception;
                sleepBeforeRetry();
            }
        }

        throw new IllegalStateException(
                "ERROR: No fue posible cargar secretos desde Vault en " + timeoutSeconds
                        + " segundos. " + safeMessage(lastFailure),
                lastFailure);
    }

    static void loadFromVault(String address, String roleId, String secretId) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        ObjectMapper mapper = new ObjectMapper();
        String normalizedAddress = normalizeAddress(address);
        String token = login(client, mapper, normalizedAddress, roleId, secretId);

        for (Map.Entry<String, SecretTarget> entry : SECRET_TARGETS.entrySet()) {
            String value = readKvValue(client, mapper, normalizedAddress, token, entry.getKey());
            SecretTarget target = entry.getValue();
            SecretLoader.setResolved(target.secretName(), target.propertyName(), value);
        }
    }

    private static String login(HttpClient client, ObjectMapper mapper, String address,
            String roleId, String secretId) {
        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "role_id", roleId,
                    "secret_id", secretId));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(address + "/v1/auth/approle/login"))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());
            requireSuccess("autenticación AppRole", response.statusCode());
            String token = mapper.readTree(response.body())
                    .path("auth").path("client_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException(
                        "ERROR: Vault no devolvió client_token para el AppRole auth-service.");
            }
            return token;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "ERROR: Respuesta inválida durante la autenticación AppRole de Vault.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "ERROR: No fue posible conectar con Vault para autenticar el AppRole.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "ERROR: Se interrumpió la autenticación AppRole de Vault.", exception);
        }
    }

    private static String readKvValue(HttpClient client, ObjectMapper mapper, String address,
            String token, String vaultPath) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(address + "/v1/secret/data/" + vaultPath))
                    .timeout(HTTP_TIMEOUT)
                    .header("X-Vault-Token", token)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());
            requireSuccess("lectura de " + vaultPath, response.statusCode());
            JsonNode valueNode = mapper.readTree(response.body())
                    .path("data").path("data").path("value");
            if (!valueNode.isTextual()) {
                throw new IllegalStateException(
                        "ERROR: Vault no devolvió el campo value para " + vaultPath + ".");
            }
            return valueNode.asText();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "ERROR: Respuesta KV v2 inválida para " + vaultPath + ".", exception);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "ERROR: No fue posible leer " + vaultPath + " desde Vault.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "ERROR: Se interrumpió la lectura de " + vaultPath + " desde Vault.", exception);
        }
    }

    private static void requireSuccess(String operation, int statusCode) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException(
                    "ERROR: Vault rechazó la " + operation + " (HTTP " + statusCode + ").");
        }
    }

    private static String readCredential(Path path, String label) {
        try {
            if (!Files.exists(path)) {
                throw new IllegalStateException(
                        "ERROR: " + label + " de Vault no disponible en " + path + ".");
            }
            String value = Files.readString(path).strip();
            if (value.isBlank()) {
                throw new IllegalStateException(
                        "ERROR: " + label + " de Vault está vacío en " + path + ".");
            }
            if (value.startsWith("REEMPLAZAR") || value.startsWith("GESTIONADO_EN_SECRETS")) {
                throw new IllegalStateException(
                        "ERROR: " + label + " de Vault contiene un marcador inseguro en "
                                + path + ".");
            }
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "ERROR: No se pudo leer " + label + " de Vault desde " + path + ".", exception);
        }
    }

    private static int parseTimeout(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return 60;
        }
        try {
            int value = Integer.parseInt(rawValue);
            if (value < 1 || value > 300) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "ERROR: VAULT_STARTUP_TIMEOUT_SECONDS debe estar entre 1 y 300.");
        }
    }

    private static String normalizeAddress(String address) {
        String normalized = address.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("ERROR: VAULT_ADDR no es una URI válida.", exception);
        }
        if (uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme())
                        || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("ERROR: VAULT_ADDR debe usar http o https y contener host.");
        }
        return normalized;
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "ERROR: Se interrumpió la espera del bootstrap de Vault.", exception);
        }
    }

    private static String safeMessage(IllegalStateException failure) {
        return failure == null || failure.getMessage() == null
                ? "Causa no disponible."
                : failure.getMessage();
    }

    private static Map<String, SecretTarget> secretTargets() {
        Map<String, SecretTarget> targets = new LinkedHashMap<>();
        targets.put("auth-service/db-password", new SecretTarget("db_auth_password", "DB_PASSWORD"));
        targets.put("auth-service/broker-password", new SecretTarget("broker_password", "RABBITMQ_PASSWORD"));
        targets.put("auth-service/jwt-secret", new SecretTarget("jwt_secret", "JWT_SECRET"));
        targets.put("auth-service/admin-password", new SecretTarget("auth_admin_password", "ADMIN_PASSWORD"));
        return Map.copyOf(targets);
    }

    private record SecretTarget(String secretName, String propertyName) {
    }
}
