package steps;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
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

    /** Cuenta efímera creada por el flujo de onboarding real (auth-service le asigna el rol USER). */
    private record CuentaEfimera(String id, String email, String password) {
    }

    @Dado("que ya hay un empleado activo con su contraseña puesta")
    public void crearEmpleadoActivo() {
        String adminToken = context.token()
                .orElseThrow(() -> new IllegalStateException("El fixture requiere autenticación ADMIN previa"));

        CuentaEfimera cuenta = crearEmpleadoConCuenta(adminToken, "EMP");
        context.put("empleadoId", cuenta.id());
        context.put("empleadoEmail", cuenta.email());
        context.put("empleadoPassword", cuenta.password());
    }

    @Dado("que entro como {string}")
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
                if (email.isBlank() || password.isBlank()) {
                    // Sin credenciales USER configuradas, la suite crea una cuenta efímera con rol USER.
                    CuentaEfimera cuenta = crearEmpleadoConCuenta(loginAdmin(), "USR");
                    email = cuenta.email();
                    password = cuenta.password();
                }
            }
            default -> throw new IllegalArgumentException("Rol de prueba no soportado: " + rol);
        }

        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Faltan las credenciales E2E del rol " + rolNormalizado
                    + ". Configure E2E_" + rolNormalizado + "_EMAIL y E2E_"
                    + rolNormalizado + "_PASSWORD");
        }

        context.setToken(login(email, password, rolNormalizado));
    }

    @Entonces("me devuelve un {int}")
    public void verificarCodigo(int codigoEsperado) {
        context.lastResponse().then().statusCode(codigoEsperado);
    }

    private String loginAdmin() {
        return login(E2EConfig.adminEmail(), E2EConfig.adminPassword(), "ADMIN");
    }

    private String login(String email, String password, String rol) {
        String payload = "{ \"email\": \"" + email + "\", \"password\": \"" + password + "\" }";
        // Se reintenta solo ante timeout o 5xx (sistema lento o circuit breaker abierto); un 401 falla de inmediato.
        Response response = Polling.until(
                "el login del rol " + rol + " debe obtener respuesta del sistema (no 5xx)",
                () -> RestAssured.given()
                        .baseUri(context.baseUrl())
                        .contentType(ContentType.JSON)
                        .body(payload)
                        .post("/auth/login"),
                current -> current.statusCode() < 500);

        Assertions.assertEquals(200, response.statusCode(),
                "El login del rol " + rol + " falló. Respuesta: " + response.asString());
        String token = response.jsonPath().getString("token");
        Assertions.assertNotNull(token, "El login no devolvió un token para el rol " + rol);
        Assertions.assertFalse(token.isBlank(), "El login devolvió un token vacío para el rol " + rol);
        return token;
    }

    /**
     * Registra un empleado nuevo, espera a que auth-service cree su cuenta de forma asincrónica,
     * le establece contraseña y comprueba que puede iniciar sesión. Cada llamada usa una identidad
     * propia (UUID) y deja registrada su limpieza.
     */
    private CuentaEfimera crearEmpleadoConCuenta(String adminToken, String prefijo) {
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
        String email = prefijo.toLowerCase(Locale.ROOT) + "-" + uid + "@empresa.com";
        String empId = prefijo + "-" + uid;

        String payload = "{ \"id\": \"" + empId + "\", \"nombre\": \"Prueba\", \"apellido\": \"Test\", \"email\": \""
                + email + "\", \"numeroEmpleado\": \"" + empId + "\", \"cargo\": \"QA\", \"area\": \"Sistemas\", "
                + "\"departamentoId\": \"" + FIXTURE_DEPARTMENT_ID + "\", \"fechaIngreso\": \""
                + LocalDate.now(ZoneOffset.UTC) + "\" }";
        Response res = RestAssured.given()
            .baseUri(context.baseUrl())
            .header("Authorization", "Bearer " + adminToken)
            .contentType(ContentType.JSON)
            .body(payload)
            .post("/empleados");

        Assertions.assertEquals(201, res.statusCode(),
                "No se pudo crear el empleado de prueba. Respuesta: " + res.asString());

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

        return new CuentaEfimera(empId, email, FIXTURE_PASSWORD);
    }
}
