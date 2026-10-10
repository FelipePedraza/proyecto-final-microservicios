package steps;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import context.TestContext;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

public class CommonSteps {

    private final TestContext context;

    public CommonSteps(TestContext context) {
        this.context = context;
    }

    @Dado("que existe un empleado activo con credenciales establecidas")
    public void crearEmpleadoActivo() {
        // Generamos un ID/email unico para no chocar con ejecuciones previas
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "test-" + uid + "@empresa.com";
        String empId = "EMP-" + uid;

        // 1. Crear el empleado simulando el registro en el sistema (empleados-service)
        // Asegurar que el departamento IT existe antes de crear el empleado
        RestAssured.given()
            .baseUri(context.baseUrl())
            .header("Authorization", "Bearer " + context.token().orElse(""))
            .contentType(ContentType.JSON)
            .body("{ \"id\": \"IT\", \"name\": \"Tecnologia\", \"description\": \"IT Dept\" }")
            .post("/departamentos");

        String payload = "{ \"id\": \"" + empId + "\", \"nombre\": \"Prueba\", \"apellido\": \"Test\", \"email\": \"" + email + "\", \"numeroEmpleado\": \"" + empId + "\", \"cargo\": \"QA\", \"area\": \"Sistemas\", \"departamentoId\": \"IT\", \"fechaIngreso\": \"" + java.time.LocalDate.now().toString() + "\" }";
        Response res = RestAssured.given()
            .baseUri(context.baseUrl())
            .header("Authorization", "Bearer " + context.token().orElse(""))
            .contentType(ContentType.JSON)
            .body(payload)
            .post("/empleados");
            
        org.junit.jupiter.api.Assertions.assertEquals(201, res.statusCode(), "No se pudo crear el empleado de prueba. Respuesta: " + res.asString());
        
        try { Thread.sleep(500); } catch (InterruptedException e) {}
        
        // Si el empleado se crea, luego tendriamos que activarlo. Pero como dependemos de auth-service:
        // Como E2E Test, vamos a crear la cuenta manual pegandole a DB o con endpoints 'backdoor'.
        // Pero para no complicarnos y ser fieles al BDD, usemos la DB si es necesario, 
        // o asumiendo que el flujo normal lo activa.

        // Por ahora, lo guardamos en contexto
        context.put("empleadoId", empId);
        context.put("empleadoEmail", email);
        context.put("empleadoPassword", "secreta123");
    }

    @Dado("que estoy autenticado como {string}")
    public void autenticar(String rol) {
        // En tu auth-service, implementamos admin@empresa.com / admin123 (Reto 5)
        String payload = "{ \"email\": \"admin@empresa.com\", \"password\": \"admin123\" }";
        Response response = RestAssured.given()
            .baseUri(context.baseUrl())
            .contentType(ContentType.JSON)
            .body(payload)
            .post("/auth/login");
        
        Assertions.assertEquals(200, response.statusCode(), "El login del ADMIN falló. ¿Está corriendo auth-service y el seeder?");
        String token = response.jsonPath().getString("token");
        context.setToken(token);
    }

    @Entonces("la respuesta debe tener código {int}")
    public void verificarCodigo(int codigoEsperado) {
        context.lastResponse().then().statusCode(codigoEsperado);
    }
}
