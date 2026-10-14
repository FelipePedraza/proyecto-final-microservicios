package steps;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import config.E2EConfig;
import context.TestContext;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.restassured.response.Response;
import support.Polling;

public class SmokeSteps {

    private final TestContext context;

    public SmokeSteps(TestContext context) {
        this.context = context;
    }

    @Dado("que el sistema está arriba y responde por el gateway")
    public void systemIsDeployed() {
        int attempts = Math.max(1, E2EConfig.startupTimeoutSeconds() / 2);
        Polling.until(
                "el gateway debe responder en " + context.baseUrl(),
                () -> given().get(context.baseUrl() + "/health").statusCode(),
                status -> status == 200,
                attempts,
                java.time.Duration.ofSeconds(2),
                java.time.Duration.ofSeconds(E2EConfig.startupTimeoutSeconds()));

        calentarRutas(attempts);
    }

    /**
     * /health responde antes de que auth, empleados, departamentos y vacaciones estén listos (arranque en
     * frío): las primeras peticiones tardan o reciben 503 del circuit breaker del gateway. Se calientan las
     * rutas con reintentos para que los escenarios no paguen ese costo. Es tolerante: si las credenciales
     * ADMIN no son válidas (p. ej. solo se corre el smoke), solo se espera a que /auth/login esté enrutado.
     */
    private void calentarRutas(int attempts) {
        java.time.Duration interval = java.time.Duration.ofSeconds(2);
        java.time.Duration timeout = java.time.Duration.ofSeconds(E2EConfig.startupTimeoutSeconds());
        String payload = "{ \"email\": \"" + E2EConfig.adminEmail()
                + "\", \"password\": \"" + E2EConfig.adminPassword() + "\" }";

        Response login = Polling.until(
                "/auth/login del gateway debe responder (no 5xx)",
                () -> given().contentType("application/json").body(payload).post(context.baseUrl() + "/auth/login"),
                response -> response.statusCode() < 500,
                attempts, interval, timeout);
        if (login.statusCode() != 200) {
            return;
        }
        String token = login.jsonPath().getString("token");
        for (String ruta : new String[] {"/departamentos", "/empleados", "/vacaciones", "/notificaciones/calentamiento"}) {
            Polling.until(
                    "la ruta " + ruta + " debe responder (no 5xx)",
                    () -> given().header("Authorization", "Bearer " + token).get(context.baseUrl() + ruta).statusCode(),
                    status -> status < 500,
                    attempts, interval, timeout);
        }
    }

    @Cuando("llamo a {string}")
    public void getEndpoint(String path) {
        Response response = given().get(context.baseUrl() + path);
        context.setLastResponse(response);
    }

    @Entonces("el status es {int}")
    public void statusIs(int expected) {
        assertThat(context.lastResponse().statusCode()).isEqualTo(expected);
    }

    @Entonces("el campo {string} vale {string}")
    public void jsonFieldIs(String field, String expected) {
        assertThat(context.lastResponse().jsonPath().getString(field)).isEqualTo(expected);
    }

    @Entonces("viene el campo {string}")
    public void jsonFieldPresent(String field) {
        assertThat(context.lastResponse().jsonPath().getString(field)).isNotBlank();
    }
}
