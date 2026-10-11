package config;

/**
 * Configuración de las pruebas E2E. Prioridad: variable de entorno, luego propiedad de sistema
 * ({@code -De2e.baseUrl=...}), luego el valor por defecto. Nunca se guardan secretos en el código.
 */
public final class E2EConfig {

    private E2EConfig() {
    }

    /** URL del API Gateway, único puerto de aplicación publicado por docker-compose. */
    public static String baseUrl() {
        return read("E2E_BASE_URL", "e2e.baseUrl", "http://localhost:8088");
    }

    public static String adminEmail() {
        return read("E2E_ADMIN_EMAIL", "e2e.adminEmail", "admin@empresa.com");
    }

    public static String adminPassword() {
        return read("E2E_ADMIN_PASSWORD", "e2e.adminPassword", "admin123");
    }

    public static String userEmail() {
        return read("E2E_USER_EMAIL", "e2e.userEmail", "");
    }

    public static String userPassword() {
        return read("E2E_USER_PASSWORD", "e2e.userPassword", "");
    }

    /** Tiempo máximo (segundos) de espera a que el sistema esté disponible antes de fallar. */
    public static int startupTimeoutSeconds() {
        return Integer.parseInt(read("E2E_STARTUP_TIMEOUT_SECONDS", "e2e.startupTimeoutSeconds", "60"));
    }

    public static int requestTimeoutMillis() {
        return Integer.parseInt(read("E2E_REQUEST_TIMEOUT_MS", "e2e.requestTimeoutMs", "30000"));
    }

    public static int pollingMaxAttempts() {
        return positiveInt("E2E_POLLING_MAX_ATTEMPTS", "e2e.pollingMaxAttempts", "15");
    }

    public static int pollingIntervalMillis() {
        return positiveInt("E2E_POLLING_INTERVAL_MS", "e2e.pollingIntervalMs", "2000");
    }

    public static int pollingTimeoutSeconds() {
        return positiveInt("E2E_POLLING_TIMEOUT_SECONDS", "e2e.pollingTimeoutSeconds", "30");
    }

    /** Secreto compartido solo para emitir tokens temporales dentro del entorno E2E. */
    public static String jwtSecret() {
        String value = read("E2E_JWT_SECRET", "e2e.jwtSecret", "");
        if (value.isBlank()) {
            String composeValue = System.getenv("JWT_SECRET_KEY");
            value = composeValue == null ? "" : composeValue.trim();
        }
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Configure E2E_JWT_SECRET (o JWT_SECRET_KEY) con el mismo secreto de auth-service, de al menos 32 bytes");
        }
        return value;
    }

    private static int positiveInt(String envName, String propertyName, String defaultValue) {
        int value = Integer.parseInt(read(envName, propertyName, defaultValue));
        if (value < 1) {
            throw new IllegalArgumentException(envName + " debe ser mayor que cero");
        }
        return value;
    }

    private static String read(String envName, String propertyName, String defaultValue) {
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return System.getProperty(propertyName, defaultValue).trim();
    }
}
