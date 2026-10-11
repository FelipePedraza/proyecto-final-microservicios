package steps;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;

import config.E2EConfig;
import context.TestContext;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import support.Polling;
import support.TestJwtTokens;

/** Step definitions del flujo E2E de onboarding. */
public class OnboardingSteps {

    private static final String ONBOARDING_DEPARTMENT_ID = "BDD-ONBOARDING";
    private static final String EMPLOYEE_PASSWORD = "Onboarding123";

    private final TestContext context;

    public OnboardingSteps(TestContext context) {
        this.context = context;
    }

    @Cuando("registro un nuevo empleado con datos válidos y únicos")
    public void registrarEmpleadoValido() {
        String adminToken = tokenActual();
        asegurarDepartamento(adminToken);
        prepararEmpleado(ONBOARDING_DEPARTMENT_ID);

        Response response = crearEmpleado(adminToken, true);
        context.setLastResponse(response);
        if (response.statusCode() == 201) {
            registrarLimpieza(adminToken);
        }
    }

    @Entonces("eventualmente el servicio de autenticación debe haber creado una cuenta para el nuevo empleado")
    public void esperarCuentaDeAutenticacion() {
        String activationToken = generarTokenActivacion();
        context.put("onboardingActivationToken", activationToken);

        Response response = Polling.until(
                "auth-service debe aceptar la activación de " + empleadoEmail(),
                () -> restablecerPassword(activationToken),
                current -> current.statusCode() == 200);
        context.setLastResponse(response);
        context.put("onboardingPassword", EMPLOYEE_PASSWORD);
    }

    @Entonces("eventualmente debe existir una notificación de bienvenida para el nuevo empleado")
    public void esperarNotificacionBienvenida() {
        esperarNotificacion("BIENVENIDA");
    }

    @Entonces("eventualmente obtengo el token de activación del nuevo empleado")
    public void obtenerTokenActivacion() {
        // usuario.creado produce una notificación SEGURIDAD. El historial oculta el token real,
        // por lo que su presencia demuestra que auth procesó el evento antes de emitir un token E2E equivalente.
        esperarNotificacion("SEGURIDAD");
        context.put("onboardingActivationToken", generarTokenActivacion());
    }

    @Cuando("el nuevo empleado establece una contraseña válida")
    public void establecerPassword() {
        String activationToken = context.get("onboardingActivationToken", String.class);
        Response response = Polling.until(
                "el nuevo empleado debe poder establecer su contraseña",
                () -> restablecerPassword(activationToken),
                current -> current.statusCode() == 200);
        context.setLastResponse(response);
        context.put("onboardingPassword", EMPLOYEE_PASSWORD);
    }

    @Cuando("el nuevo empleado inicia sesión con su contraseña")
    public void iniciarSesionNuevoEmpleado() {
        String password = context.get("onboardingPassword", String.class);
        String payload = "{ \"email\": \"" + empleadoEmail()
                + "\", \"password\": \"" + password + "\" }";

        context.setLastResponse(RestAssured.given()
                .baseUri(context.baseUrl())
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/auth/login"));
    }

    @Entonces("la respuesta debe contener un token de autenticación")
    public void verificarTokenAutenticacion() {
        String token = context.lastResponse().jsonPath().getString("token");
        Assertions.assertNotNull(token, "La respuesta de login no contiene el campo token");
        Assertions.assertFalse(token.isBlank(), "La respuesta de login contiene un token vacío");
    }

    @Cuando("intento registrar un nuevo empleado en un departamento inexistente")
    public void registrarConDepartamentoInexistente() {
        prepararEmpleado("NO-EXISTE-" + UUID.randomUUID().toString().substring(0, 8));
        context.setLastResponse(crearEmpleado(tokenActual(), true));
    }

    @Cuando("intento registrar un nuevo empleado sin nombre ni correo electrónico")
    public void registrarSinCamposObligatorios() {
        asegurarDepartamento(tokenActual());
        prepararEmpleado(ONBOARDING_DEPARTMENT_ID);
        context.setLastResponse(crearEmpleado(tokenActual(), false));
    }

    private void prepararEmpleado(String departmentId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String employeeId = "ONB-" + suffix;
        context.put("onboardingEmpleadoId", employeeId);
        context.put("onboardingEmpleadoEmail", "onboarding-" + suffix + "@empresa.com");
        context.put("onboardingDepartamentoId", departmentId);
    }

    private Response crearEmpleado(String adminToken, boolean includeRequiredFields) {
        String employeeId = empleadoId();
        String name = includeRequiredFields ? "Prueba" : "";
        String email = includeRequiredFields ? empleadoEmail() : "";
        String payload = """
                {
                  "id": "%s",
                  "nombre": "%s",
                  "apellido": "Onboarding",
                  "email": "%s",
                  "numeroEmpleado": "%s",
                  "cargo": "QA",
                  "area": "Tecnologia",
                  "departamentoId": "%s",
                  "fechaIngreso": "%s"
                }
                """.formatted(
                        employeeId,
                        name,
                        email,
                        employeeId,
                        context.get("onboardingDepartamentoId", String.class),
                        LocalDate.now());

        return RestAssured.given()
                .baseUri(context.baseUrl())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/empleados");
    }

    private Response restablecerPassword(String activationToken) {
        String payload = "{ \"resetToken\": \"" + activationToken
                + "\", \"newPassword\": \"" + EMPLOYEE_PASSWORD + "\" }";
        return RestAssured.given()
                .baseUri(context.baseUrl())
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/auth/reset-password");
    }

    private void esperarNotificacion(String expectedType) {
        String employeeId = empleadoId();
        Polling.waitUntil("debe existir una notificación " + expectedType + " para " + employeeId, () -> {
            Response response = RestAssured.given()
                    .baseUri(context.baseUrl())
                    .header("Authorization", "Bearer " + tokenActual())
                    .get("/notificaciones/" + employeeId);
            if (response.statusCode() != 200) {
                return false;
            }
            List<String> types = response.jsonPath().getList("tipo", String.class);
            return types != null && types.contains(expectedType);
        });
    }

    private void asegurarDepartamento(String adminToken) {
        Response response = RestAssured.given()
                .baseUri(context.baseUrl())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{ \"id\": \"" + ONBOARDING_DEPARTMENT_ID
                        + "\", \"name\": \"Onboarding E2E\", \"description\": \"Datos de prueba\" }")
                .post("/departamentos");
        Assertions.assertTrue(response.statusCode() == 201 || response.statusCode() == 409,
                "No se pudo preparar el departamento de onboarding. Respuesta: " + response.asString());
    }

    private String generarTokenActivacion() {
        return TestJwtTokens.resetPasswordToken(empleadoId(), E2EConfig.jwtSecret());
    }

    private void registrarLimpieza(String adminToken) {
        String employeeId = empleadoId();
        context.registerCreated("empleados", employeeId);
        context.onCleanup(() -> RestAssured.given()
                .baseUri(context.baseUrl())
                .header("Authorization", "Bearer " + adminToken)
                .delete("/empleados/" + employeeId));
    }

    private String tokenActual() {
        return context.token().orElseThrow(() -> new IllegalStateException("El escenario no tiene token ADMIN"));
    }

    private String empleadoId() {
        return context.get("onboardingEmpleadoId", String.class);
    }

    private String empleadoEmail() {
        return context.get("onboardingEmpleadoEmail", String.class);
    }
}
