package hooks;

import config.E2EConfig;
import context.TestContext;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;

public class Hooks {

    private final TestContext context;

    public Hooks(TestContext context) {
        this.context = context;
    }

    @Before(order = 0)
    public void configureHttpClient(Scenario scenario) {
        int timeout = E2EConfig.requestTimeoutMillis();
        RestAssured.baseURI = context.baseUrl();
        RestAssured.config = RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", timeout)
                .setParam("http.socket.timeout", timeout));
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL);
        scenario.log("Sistema bajo prueba: " + context.baseUrl());
    }

    @After(order = 0)
    public void cleanUp(Scenario scenario) {
        context.cleanupActionsInReverseOrder().forEach(action -> {
            try {
                action.run();
            } catch (RuntimeException error) {
                scenario.log("La limpieza falló y se ignora: " + error.getMessage());
            }
        });
        if (scenario.isFailed() && context.hasToken()) {
            scenario.log("El escenario falló con un token JWT activo (no se imprime por seguridad)");
        }
    }
}
