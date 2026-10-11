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

    @Dado("que el sistema está desplegado y accesible a través del gateway")
    public void systemIsDeployed() {
        int attempts = Math.max(1, E2EConfig.startupTimeoutSeconds() / 2);
        Polling.until(
                "el gateway debe responder en " + context.baseUrl(),
                () -> given().get(context.baseUrl() + "/health").statusCode(),
                status -> status == 200,
                attempts,
                java.time.Duration.ofSeconds(2),
                java.time.Duration.ofSeconds(E2EConfig.startupTimeoutSeconds()));
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
