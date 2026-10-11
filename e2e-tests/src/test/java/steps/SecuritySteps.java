package steps;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;

import config.E2EConfig;
import context.TestContext;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/** Step definitions de los escenarios de seguridad y RBAC del API Gateway. */
public class SecuritySteps {

    private static final String SECURITY_DEPARTMENT_ID = "BDD-SECURITY";

    private final TestContext context;

    public SecuritySteps(TestContext context) {
        this.context = context;
    }

    @Cuando("consulto la lista de empleados sin token de autenticación")
    public void consultarEmpleadosSinToken() {
        context.setLastResponse(RestAssured.given()
                .baseUri(context.baseUrl())
                .queryParam("estado", "RETIRADO")
                .get("/empleados"));
    }

    @Dado("que uso un token de autenticación malformado")
    public void usarTokenMalformado() {
        context.setToken("esto-no-es-un-jwt-valido");
    }

    @Cuando("consulto la lista de empleados")
    public void consultarEmpleados() {
        context.setLastResponse(RestAssured.given()
                .baseUri(context.baseUrl())
                .header("Authorization", bearerToken())
                .queryParam("estado", "RETIRADO")
                .get("/empleados"));
    }

    @Cuando("intento registrar un empleado con datos válidos")
    public void intentarRegistrarEmpleado() {
        String empleadoId = prepararDatosEmpleado();
        context.setLastResponse(crearEmpleado(empleadoId, bearerToken()));
    }

    @Dado("que existe un empleado de prueba para verificar el control de acceso")
    public void crearEmpleadoDePrueba() {
        String adminToken = login(E2EConfig.adminEmail(), E2EConfig.adminPassword(), "ADMIN");
        asegurarDepartamento(adminToken);

        String empleadoId = prepararDatosEmpleado();
        Response response = crearEmpleado(empleadoId, "Bearer " + adminToken);
        Assertions.assertEquals(201, response.statusCode(),
                "No se pudo preparar el empleado del escenario de seguridad. Respuesta: " + response.asString());
        registrarLimpieza(empleadoId, adminToken);
    }

    @Cuando("intento eliminar el empleado de prueba")
    public void intentarEliminarEmpleado() {
        eliminarEmpleadoConToken(bearerToken());
    }

    @Cuando("registro un empleado con datos válidos")
    public void registrarEmpleadoComoAdministrador() {
        String adminToken = context.token()
                .orElseThrow(() -> new IllegalStateException("El escenario no tiene un token ADMIN"));
        asegurarDepartamento(adminToken);

        String empleadoId = prepararDatosEmpleado();
        context.setLastResponse(crearEmpleado(empleadoId, "Bearer " + adminToken));
        if (context.lastResponse().statusCode() == 201) {
            registrarLimpieza(empleadoId, adminToken);
        }
    }

    @Cuando("elimino el empleado registrado")
    public void eliminarEmpleadoRegistrado() {
        eliminarEmpleadoConToken(bearerToken());
    }

    private String prepararDatosEmpleado() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String empleadoId = "SEC-" + sufijo;
        context.put("securityEmpleadoId", empleadoId);
        context.put("securityEmpleadoEmail", "security-" + sufijo + "@empresa.com");
        return empleadoId;
    }

    private Response crearEmpleado(String empleadoId, String authorization) {
        String email = context.get("securityEmpleadoEmail", String.class);
        String payload = """
                {
                  "id": "%s",
                  "nombre": "Prueba",
                  "apellido": "Seguridad",
                  "email": "%s",
                  "numeroEmpleado": "%s",
                  "cargo": "QA",
                  "area": "Tecnologia",
                  "departamentoId": "%s",
                  "fechaIngreso": "%s"
                }
                """.formatted(empleadoId, email, empleadoId, SECURITY_DEPARTMENT_ID, LocalDate.now());

        return RestAssured.given()
                .baseUri(context.baseUrl())
                .header("Authorization", authorization)
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/empleados");
    }

    private void eliminarEmpleadoConToken(String authorization) {
        String empleadoId = context.get("securityEmpleadoId", String.class);
        context.setLastResponse(RestAssured.given()
                .baseUri(context.baseUrl())
                .header("Authorization", authorization)
                .delete("/empleados/" + empleadoId));
    }

    private void asegurarDepartamento(String adminToken) {
        Response response = RestAssured.given()
                .baseUri(context.baseUrl())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{ \"id\": \"" + SECURITY_DEPARTMENT_ID
                        + "\", \"name\": \"Pruebas de seguridad\", \"description\": \"Datos E2E\" }")
                .post("/departamentos");

        Assertions.assertTrue(response.statusCode() == 201 || response.statusCode() == 409,
                "No se pudo preparar el departamento de pruebas. Respuesta: " + response.asString());
    }

    private void registrarLimpieza(String empleadoId, String adminToken) {
        context.registerCreated("empleados", empleadoId);
        context.onCleanup(() -> RestAssured.given()
                .baseUri(context.baseUrl())
                .header("Authorization", "Bearer " + adminToken)
                .delete("/empleados/" + empleadoId));
    }

    private String bearerToken() {
        return "Bearer " + context.token()
                .orElseThrow(() -> new IllegalStateException("El escenario no tiene token de autenticación"));
    }

    private String login(String email, String password, String role) {
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Faltan E2E_" + role + "_EMAIL y E2E_" + role + "_PASSWORD");
        }

        String payload = "{ \"email\": \"" + email + "\", \"password\": \"" + password + "\" }";
        Response response = RestAssured.given()
                .baseUri(context.baseUrl())
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/auth/login");
        Assertions.assertEquals(200, response.statusCode(),
                "No fue posible autenticar el rol " + role + " durante el setup. Respuesta: " + response.asString());

        String token = response.jsonPath().getString("token");
        Assertions.assertNotNull(token, "El login de " + role + " no devolvió token");
        return token;
    }
}
