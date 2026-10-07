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
        return read("E2E_ADMIN_EMAIL", "e2e.adminEmail", "");
    }

    public static String adminPassword() {
        return read("E2E_ADMIN_PASSWORD", "e2e.adminPassword", "");
    }

    /** Tiempo máximo (segundos) de espera a que el sistema esté disponible antes de fallar. */
    public static int startupTimeoutSeconds() {
        return Integer.parseInt(read("E2E_STARTUP_TIMEOUT_SECONDS", "e2e.startupTimeoutSeconds", "60"));
    }

    public static int requestTimeoutMillis() {
        return Integer.parseInt(read("E2E_REQUEST_TIMEOUT_MS", "e2e.requestTimeoutMs", "30000"));
    }

    private static String read(String envName, String propertyName, String defaultValue) {
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return System.getProperty(propertyName, defaultValue).trim();
    }
}
