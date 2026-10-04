package co.edu.uniquindio.auth_service.messaging;

import co.edu.uniquindio.auth_service.model.Account;
import co.edu.uniquindio.auth_service.model.AccountStatus;
import co.edu.uniquindio.auth_service.model.ProcessedEvent;
import co.edu.uniquindio.auth_service.model.Role;
import co.edu.uniquindio.auth_service.repository.AccountRepository;
import co.edu.uniquindio.auth_service.repository.ProcessedEventRepository;
import co.edu.uniquindio.auth_service.security.JWTUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Transiciones del ciclo de vida de una cuenta disparadas por eventos. Cada transición solo se aplica
 * desde su estado de origen, y el {@link ProcessedEvent} se guarda en la misma transacción que el cambio.
 */
@Service
public class AccountLifecycleService {

    public static final String EMPLEADO_CREADO = "empleado.creado";
    public static final String EMPLEADO_RETIRADO = "empleado.retirado";
    public static final String VACACIONES_INICIADAS = "vacaciones.iniciadas";
    public static final String VACACIONES_FINALIZADAS = "vacaciones.finalizadas";

    /** Vigencia del token de activación (el spec pide entre 15 y 60 minutos). */
    static final long ACTIVATION_TOKEN_MILLIS = 3_600_000L;

    private static final Logger log = LoggerFactory.getLogger(AccountLifecycleService.class);

    private final AccountRepository accounts;
    private final ProcessedEventRepository processedEvents;
    private final JWTUtils jwtUtils;
    private final AuthEventPublisher publisher;

    public AccountLifecycleService(
            AccountRepository accounts,
            ProcessedEventRepository processedEvents,
            JWTUtils jwtUtils,
            AuthEventPublisher publisher) {
        this.accounts = accounts;
        this.processedEvents = processedEvents;
        this.jwtUtils = jwtUtils;
        this.publisher = publisher;
    }

    public static boolean isLifecycleEvent(String type) {
        return EMPLEADO_CREADO.equals(type)
                || EMPLEADO_RETIRADO.equals(type)
                || VACACIONES_INICIADAS.equals(type)
                || VACACIONES_FINALIZADAS.equals(type);
    }

    /**
     * @param email solo se usa en {@code empleado.creado}; en el resto se toma de la cuenta existente.
     */
    @Transactional
    public void process(String eventId, String type, String empleadoId, String email) {
        if (processedEvents.existsById(eventId)) {
            log.info("Evento {} ({}) ya procesado; se ignora (deduplicación).", eventId, type);
            return;
        }

        switch (type) {
            case EMPLEADO_CREADO -> onEmpleadoCreado(empleadoId, email);
            case EMPLEADO_RETIRADO -> onEmpleadoRetirado(empleadoId);
            case VACACIONES_INICIADAS -> onVacacionesIniciadas(empleadoId);
            case VACACIONES_FINALIZADAS -> onVacacionesFinalizadas(empleadoId);
            default -> {
                log.debug("Evento {} de tipo {} no pertenece al ciclo de vida de cuentas; se ignora.", eventId, type);
                return;
            }
        }

        processedEvents.save(new ProcessedEvent(eventId, Instant.now()));
    }

    private void onEmpleadoCreado(String empleadoId, String email) {
        if (accounts.existsById(empleadoId)) {
            log.info("empleado.creado: ya existe una cuenta para el empleado {}; no se recrea ni se republica usuario.creado.", empleadoId);
            return;
        }
        if (accounts.findByEmail(email).isPresent()) {
            log.warn("empleado.creado: el email {} del empleado {} ya pertenece a otra cuenta; no se crea la cuenta.", email, empleadoId);
            return;
        }

        accounts.save(Account.builder()
                .id(empleadoId)
                .email(email)
                .password(null)
                .role(Role.USER)
                .status(AccountStatus.PENDIENTE_ACTIVACION)
                .build());

        String token = jwtUtils.generateToken(empleadoId, Map.of("type", "RESET_PASSWORD"), ACTIVATION_TOKEN_MILLIS);
        String expiraEn = Instant.now().plusMillis(ACTIVATION_TOKEN_MILLIS).toString();
        publisher.publish(AuthEventPayloads.USUARIO_CREADO,
                AuthEventPayloads.usuarioCreado(empleadoId, email, token, expiraEn));
        log.info("Cuenta creada en PENDIENTE_ACTIVACION para el empleado {}.", empleadoId);
    }

    private void onEmpleadoRetirado(String empleadoId) {
        Account account = accounts.findById(empleadoId).orElse(null);
        if (account == null) {
            log.warn("empleado.retirado: no existe cuenta para el empleado {}; se confirma el mensaje.", empleadoId);
            return;
        }

        account.setStatus(AccountStatus.DESACTIVADA_PERMANENTE);
        accounts.save(account);
        publisher.publish(AuthEventPayloads.CUENTA_DESACTIVADA,
                AuthEventPayloads.cuentaDesactivada(account.getId(), account.getEmail(), AuthEventPayloads.MOTIVO_RETIRO, true));
        log.info("Cuenta del empleado {} pasó a DESACTIVADA_PERMANENTE por retiro.", empleadoId);
    }

    private void onVacacionesIniciadas(String empleadoId) {
        Account account = accounts.findById(empleadoId).orElse(null);
        if (account == null) {
            log.warn("vacaciones.iniciadas: no existe cuenta para el empleado {}; se confirma el mensaje.", empleadoId);
            return;
        }
        if (account.getStatus() != AccountStatus.ACTIVA) {
            log.info("vacaciones.iniciadas: la cuenta del empleado {} está {} (no ACTIVA); no se suspende.", empleadoId, account.getStatus());
            return;
        }

        account.setStatus(AccountStatus.SUSPENDIDA_TEMPORAL);
        accounts.save(account);
        publisher.publish(AuthEventPayloads.CUENTA_DESACTIVADA,
                AuthEventPayloads.cuentaDesactivada(account.getId(), account.getEmail(), AuthEventPayloads.MOTIVO_VACACIONES, false));
        log.info("Cuenta del empleado {} pasó a SUSPENDIDA_TEMPORAL por vacaciones.", empleadoId);
    }

    private void onVacacionesFinalizadas(String empleadoId) {
        Account account = accounts.findById(empleadoId).orElse(null);
        if (account == null) {
            log.warn("vacaciones.finalizadas: no existe cuenta para el empleado {}; se confirma el mensaje.", empleadoId);
            return;
        }
        if (account.getStatus() == AccountStatus.DESACTIVADA_PERMANENTE) {
            log.info("vacaciones.finalizadas ignorado: la cuenta del empleado {} está DESACTIVADA_PERMANENTE (retirado durante sus vacaciones); no se reactiva.", empleadoId);
            return;
        }
        if (account.getStatus() != AccountStatus.SUSPENDIDA_TEMPORAL) {
            log.info("vacaciones.finalizadas: la cuenta del empleado {} está {} (no SUSPENDIDA_TEMPORAL); no hay nada que reactivar.", empleadoId, account.getStatus());
            return;
        }

        account.setStatus(AccountStatus.ACTIVA);
        accounts.save(account);
        publisher.publish(AuthEventPayloads.CUENTA_ACTIVADA,
                AuthEventPayloads.cuentaActivada(account.getId(), account.getEmail(), AuthEventPayloads.MOTIVO_FIN_VACACIONES));
        log.info("Cuenta del empleado {} reactivada (ACTIVA) al terminar sus vacaciones.", empleadoId);
    }
}
