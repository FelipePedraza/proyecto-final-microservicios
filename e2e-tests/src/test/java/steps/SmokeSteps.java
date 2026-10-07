package steps;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import config.E2EConfig;
import context.TestContext;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.restassured.response.Response;

public class SmokeSteps {

    private final TestContext context;

    public SmokeSteps(TestContext context) {
        this.context = context;
    }

    @Dado("que el sistema está desplegado y accesible a través del gateway")
    public void systemIsDeployed() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(E2EConfig.startupTimeoutSeconds()));
        Exception lastError = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                given().get(context.baseUrl() + "/health").then().statusCode(200);
                return;
            } catch (Exception error) {
                lastError = error;
                Thread.sleep(2000);
            }
        }
        throw new AssertionError("El gateway no respondió en " + context.baseUrl()
                + " tras " + E2EConfig.startupTimeoutSeconds() + " s. ¿Está levantado con 'docker compose up -d'?",
                lastError);
    }

    @Cuando("consulto el endpoint {string}")
    public void getEndpoint(String path) {
        Response response = given().get(context.baseUrl() + path);
        context.setLastResponse(response);
    }

    @Entonces("la respuesta HTTP es {int}")
    public void statusIs(int expected) {
        assertThat(context.lastResponse().statusCode()).isEqualTo(expected);
    }

    @Entonces("el campo {string} de la respuesta es {string}")
    public void jsonFieldIs(String field, String expected) {
        assertThat(context.lastResponse().jsonPath().getString(field)).isEqualTo(expected);
    }

    @Entonces("la respuesta contiene el campo {string}")
    public void jsonFieldPresent(String field) {
        assertThat(context.lastResponse().jsonPath().getString(field)).isNotBlank();
    }
}
