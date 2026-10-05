package co.edu.uniquindio.auth_service.messaging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Único lugar donde se construye el {@code data} de los eventos que publica el ciclo de vida de cuentas.
 * Si el Catálogo de Eventos cambia algún campo, se ajusta aquí y en ningún otro sitio.
 *
 * <p>Estado: los campos coinciden con los que lee {@code eventoParser.js} de notificaciones-service (verificado
 * de punta a punta con Docker). Pendiente: confirmarlos contra el Catálogo de Eventos 3.x.
 */
public final class AuthEventPayloads {

    public static final String USUARIO_CREADO = "usuario.creado";
    public static final String CUENTA_ACTIVADA = "cuenta.activada";
    public static final String CUENTA_DESACTIVADA = "cuenta.desactivada";

    public static final String MOTIVO_RETIRO = "RETIRO";
    public static final String MOTIVO_VACACIONES = "VACACIONES";
    public static final String MOTIVO_FIN_VACACIONES = "FIN_VACACIONES";

    private AuthEventPayloads() {}

    // usuario.creado: campos empleadoId, email,
    //  tokenActivacion (por analogía con tokenRecuperacion) y expiraEn (igual que usuario.recuperacion).
    public static Map<String, Object> usuarioCreado(String empleadoId, String email, String tokenActivacion, String expiraEn) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("empleadoId", empleadoId);
        data.put("email", email);
        data.put("tokenActivacion", tokenActivacion);
        data.put("expiraEn", expiraEn);
        return data;
    }

    // cuenta.desactivada: campos empleadoId, email,
    //  motivo ("RETIRO" | "VACACIONES"), permanente (boolean).
    public static Map<String, Object> cuentaDesactivada(String empleadoId, String email, String motivo, boolean permanente) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("empleadoId", empleadoId);
        data.put("email", email);
        data.put("motivo", motivo);
        data.put("permanente", permanente);
        return data;
    }

    // cuenta.activada: campos empleadoId, email, motivo,
    //  los mismos que ya publica AuthServiceImpl.resetPassword con motivo ACTIVACION_INICIAL.
    public static Map<String, Object> cuentaActivada(String empleadoId, String email, String motivo) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("empleadoId", empleadoId);
        data.put("email", email);
        data.put("motivo", motivo);
        return data;
    }
}
