package steps;

import context.TestContext;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.LocalDate;
import java.time.ZoneOffset;
import support.Polling;

/**
 * Pasos de vacaciones. Las fechas se calculan en UTC porque vacaciones-service valida contra
 * {@code Clock.systemUTC()}: con la hora local, de noche "hoy" sería una fecha pasada para el servicio.
 */
public class VacacionesSteps {

    private final TestContext context;

    public VacacionesSteps(TestContext context) {
        this.context = context;
    }

    @Cuando("programo un período de vacaciones desde dentro de {int} días hasta dentro de {int} días")
    public void programoVacaciones(int diasInicio, int diasFin) {
        LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
        String body = "{ \"empleadoId\": \"" + context.get("empleadoId", String.class)
                + "\", \"fechaInicio\": \"" + hoy.plusDays(diasInicio)
                + "\", \"fechaFin\": \"" + hoy.plusDays(diasFin) + "\" }";

        Response res = autenticada()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/vacaciones");

        context.setLastResponse(res);
        if (res.statusCode() == 201) {
            context.put("vacacionId", res.jsonPath().getString("id"));
        }
    }

    // El scheduler real corre por cron y avanza por fecha; para no esperar días se usan los
    // endpoints manuales de vacaciones-service (requieren VACACIONES_MANUAL_ENABLED=true).
    @Cuando("fuerzo el inicio del período de vacaciones programado")
    public void fuerzoInicio() {
        context.setLastResponse(transicionManual("forzar-inicio"));
    }

    @Cuando("fuerzo el fin del período de vacaciones en curso")
    public void fuerzoFin() {
        context.setLastResponse(transicionManual("forzar-fin"));
    }

    @Entonces("eventualmente el empleado no puede iniciar sesión")
    public void eventualmenteNoPuedeIniciarSesion() {
        Polling.waitUntil("la cuenta del empleado debe quedar suspendida", () -> {
            int status = intentarLogin().statusCode();
            return status == 401 || status == 403;
        });
    }

    @Entonces("eventualmente el empleado puede iniciar sesión nuevamente")
    public void eventualmentePuedeIniciarSesion() {
        Polling.waitUntil("la cuenta del empleado debe quedar activa nuevamente",
                () -> intentarLogin().statusCode() == 200);
    }

    private Response transicionManual(String accion) {
        return autenticada().post("/vacaciones/" + context.get("vacacionId", String.class) + "/" + accion);
    }

    private io.restassured.specification.RequestSpecification autenticada() {
        return RestAssured.given()
                .baseUri(context.baseUrl())
                .header("Authorization", "Bearer " + context.token().orElse(""));
    }

    private Response intentarLogin() {
        String payload = "{ \"email\": \"" + context.get("empleadoEmail", String.class)
                + "\", \"password\": \"" + context.get("empleadoPassword", String.class) + "\" }";

        return RestAssured.given()
                .baseUri(context.baseUrl())
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/auth/login");
    }
}
