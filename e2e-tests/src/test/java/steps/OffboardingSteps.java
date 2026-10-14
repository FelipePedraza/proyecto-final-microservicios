package steps;

import context.TestContext;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;

public class OffboardingSteps {

    private final TestContext context;

    public OffboardingSteps(TestContext context) {
        this.context = context;
    }

    @Cuando("doy de baja al empleado")
    public void registroDesvinculacion() {
        String empId = context.get("empleadoId", String.class);

        // Reto 1/2: El offboarding se hace con un DELETE a /empleados/{id} 
        // o a veces un POST /empleados/{id}/retirar (depende de la implementación, usaremos DELETE estándar)
        Response res = RestAssured.given()
            .baseUri(context.baseUrl())
            .header("Authorization", "Bearer " + context.token().orElse(""))
            .delete("/empleados/" + empId);

        context.setLastResponse(res);
    }

    @Entonces("tampoco puede recuperar su contraseña")
    public void recuperacionContrasenaFalla() {
        String email = context.get("empleadoEmail", String.class);
        
        // Reto 5: Auth Service POST /auth/recover-password ignora cuentas RETIRADAS silenciosamente (Status 200) por seguridad
        String body = "{ \"email\": \"" + email + "\" }";
        
        Response res = RestAssured.given()
            .baseUri(context.baseUrl())
            .contentType(ContentType.JSON)
            .body(body)
            .post("/auth/recover-password");

        Assertions.assertTrue(res.statusCode() == 200, 
            "Esperaba status 200 (silencioso por seguridad) en recuperación para retirados, pero dio " + res.statusCode());
    }

    @Cuando("listo los empleados en estado {string}")
    public void consultoListaEmpleados(String estado) {
        Response res = RestAssured.given()
            .baseUri(context.baseUrl())
            .header("Authorization", "Bearer " + context.token().orElse(""))
            .queryParam("estado", estado)
            .get("/empleados");

        context.setLastResponse(res);
    }

    @Entonces("el empleado dado de baja sale en la lista")
    public void empleadoRetiradoAparece() {
        String empId = context.get("empleadoId", String.class);
        String body = context.lastResponse().asString();
        
        Assertions.assertTrue(body.contains(empId), "El empleado no apareció en la consulta de auditoría");
    }
}
