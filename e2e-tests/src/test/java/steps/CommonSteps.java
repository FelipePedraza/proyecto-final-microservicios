package steps;

import java.util.UUID;
import java.util.Locale;
import org.junit.jupiter.api.Assertions;
import config.E2EConfig;
import context.TestContext;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import support.Polling;
import support.TestJwtTokens;

public class CommonSteps {

    private static final String FIXTURE_DEPARTMENT_ID = "BDD-FIXTURES";
    private static final String FIXTURE_PASSWORD = "Fixture123";

    private final TestContext context;

    public CommonSteps(TestContext context) {
        this.context = context;
    }

    @Dado("que existe un empleado activo con credenciales establecidas")
    public void crearEmpleadoActivo() {
        String adminToken = context.token()
                .orElseThrow(() -> new IllegalStateException("El fixture requiere autenticación ADMIN previa"));

        // El departamento es un catálogo compartido estable. 409 significa que otra ejecución ya lo creó.
        Response departmentResponse = RestAssured.given()
            .baseUri(context.baseUrl())
            .header("Authorization", "Bearer " + adminToken)
            .contentType(ContentType.JSON)
            .body("{ \"id\": \"" + FIXTURE_DEPARTMENT_ID
                    + "\", \"name\": \"Fixtures BDD\", \"description\": \"Datos compartidos de prueba\" }")
            .post("/departamentos");
        Assertions.assertTrue(departmentResponse.statusCode() == 201 || departmentResponse.statusCode() == 409,
                "No se pudo preparar el departamento del fixture. Respuesta: " + departmentResponse.asString());

        // Cada escenario recibe identidad propia; nunca reutiliza empleados de otro escenario.
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "test-" + uid + "@empresa.com";
        String empId = "EMP-" + uid;

        String payload = "{ \"id\": \"" + empId + "\", \"nombre\": \"Prueba\", \"apellido\": \"Test\", \"email\": \"" + email + "\", \"numeroEmpleado\": \"" + empId + "\", \"cargo\": \"QA\", \"area\": \"Sistemas\", \"departamentoId\": \"" + FIXTURE_DEPARTMENT_ID + "\", \"fechaIngreso\": \"" + java.time.LocalDate.now().toString() + "\" }";
        Response res = RestAssured.given()
            .baseUri(context.baseUrl())
            .header("Authorization", "Bearer " + adminToken)
            .contentType(ContentType.JSON)
            .body(payload)
            .post("/empleados");
            
        Assertions.assertEquals(201, res.statusCode(),
                "No se pudo crear el empleado de prueba. Respuesta: " + res.asString());

        context.put("empleadoId", empId);
        context.put("empleadoEmail", email);
        context.put("empleadoPassword", FIXTURE_PASSWORD);
        context.registerCreated("empleados", empId);
        context.onCleanup(() -> RestAssured.given()
                .baseUri(context.baseUrl())
                .header("Authorization", "Bearer " + adminToken)
                .delete("/empleados/" + empId));

        String activationToken = TestJwtTokens.resetPasswordToken(empId, E2EConfig.jwtSecret());
        Polling.waitUntil("auth-service debe crear y activar la cuenta del fixture " + empId, () -> {
            String resetPayload = "{ \"resetToken\": \"" + activationToken
                    + "\", \"newPassword\": \"" + FIXTURE_PASSWORD + "\" }";
            return RestAssured.given()
                    .baseUri(context.baseUrl())
                    .contentType(ContentType.JSON)
                    .body(resetPayload)
                    .post("/auth/reset-password")
                    .statusCode() == 200;
        });

        Polling.waitUntil("el fixture debe iniciar sesión antes de ejecutar el escenario", () -> {
            String loginPayload = "{ \"email\": \"" + email
                    + "\", \"password\": \"" + FIXTURE_PASSWORD + "\" }";
            return RestAssured.given()
                    .baseUri(context.baseUrl())
                    .contentType(ContentType.JSON)
                    .body(loginPayload)
                    .post("/auth/login")
                    .statusCode() == 200;
        });
    }

    @Dado("que estoy autenticado como {string}")
    public void autenticar(String rol) {
        String rolNormalizado = rol.trim().toUpperCase(Locale.ROOT);
        String email;
        String password;

        switch (rolNormalizado) {
            case "ADMIN" -> {
                email = E2EConfig.adminEmail();
                password = E2EConfig.adminPassword();
            }
            case "USER" -> {
                email = E2EConfig.userEmail();
                password = E2EConfig.userPassword();
            }
            default -> throw new IllegalArgumentException("Rol de prueba no soportado: " + rol);
        }

        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Faltan las credenciales E2E del rol " + rolNormalizado
                    + ". Configure E2E_" + rolNormalizado + "_EMAIL y E2E_"
                    + rolNormalizado + "_PASSWORD");
        }

        String payload = "{ \"email\": \"" + email + "\", \"password\": \"" + password + "\" }";
        Response response = RestAssured.given()
            .baseUri(context.baseUrl())
            .contentType(ContentType.JSON)
            .body(payload)
            .post("/auth/login");
        
        Assertions.assertEquals(200, response.statusCode(),
                "El login del rol " + rolNormalizado + " falló. Respuesta: " + response.asString());
        String token = response.jsonPath().getString("token");
        Assertions.assertNotNull(token, "El login no devolvió un token para el rol " + rolNormalizado);
        Assertions.assertFalse(token.isBlank(), "El login devolvió un token vacío para el rol " + rolNormalizado);
        context.setToken(token);
    }

    @Entonces("la respuesta debe tener código {int}")
    public void verificarCodigo(int codigoEsperado) {
        context.lastResponse().then().statusCode(codigoEsperado);
    }
}
