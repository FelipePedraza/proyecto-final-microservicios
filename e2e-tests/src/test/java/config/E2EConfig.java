package config;

/**
 * Configuración E2E: variable de entorno, propiedad de sistema y valores predeterminados no sensibles.
 * Las credenciales se deben proporcionar explícitamente; nunca se guardan secretos en el código.
 */
public final class E2EConfig {

    private E2EConfig() {
    }

    /** URL del API Gateway, único puerto de aplicación publicado por docker-compose. */
    public static String baseUrl() {
        String value = read("E2E_BASE_URL", "e2e.baseUrl", "");
        if (value.isBlank()) {
            throw new IllegalStateException("Configure E2E_BASE_URL antes de ejecutar los tests");
        }
        return value;
    }

    public static String adminEmail() {
        String value = read("E2E_ADMIN_EMAIL", "e2e.adminEmail", "");
        if (value.isBlank()) {
            throw new IllegalStateException("Configure E2E_ADMIN_EMAIL before running ADMIN scenarios");
        }
        return value;
    }

    public static String adminPassword() {
        String value = read("E2E_ADMIN_PASSWORD", "e2e.adminPassword", "");
        if (value.isBlank()) {
            throw new IllegalStateException("Configure E2E_ADMIN_PASSWORD before running ADMIN scenarios");
        }
        return value;
    }

    public static String userEmail() {
        String value = read("E2E_USER_EMAIL", "e2e.userEmail", "");
        if (value.isBlank()) {
            throw new IllegalStateException("Configure E2E_USER_EMAIL before running USER scenarios");
        }
        return value;
    }

    public static String userPassword() {
        String value = read("E2E_USER_PASSWORD", "e2e.userPassword", "");
        if (value.isBlank()) {
            throw new IllegalStateException("Configure E2E_USER_PASSWORD before running USER scenarios");
        }
        return value;
    }

    /** Tiempo máximo (segundos) de espera a que el sistema esté disponible antes de fallar. */
    public static int startupTimeoutSeconds() {
        return Integer.parseInt(read("E2E_STARTUP_TIMEOUT_SECONDS", "e2e.startupTimeoutSeconds", ""));
    }

    public static int requestTimeoutMillis() {
        return Integer.parseInt(read("E2E_REQUEST_TIMEOUT_MS", "e2e.requestTimeoutMs", ""));
    }

    public static int pollingMaxAttempts() {
        return positiveInt("E2E_POLLING_MAX_ATTEMPTS", "e2e.pollingMaxAttempts", "");
    }

    public static int pollingIntervalMillis() {
        return positiveInt("E2E_POLLING_INTERVAL_MS", "e2e.pollingIntervalMs", "");
    }

    public static int pollingTimeoutSeconds() {
        return positiveInt("E2E_POLLING_TIMEOUT_SECONDS", "e2e.pollingTimeoutSeconds", "");
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
