package co.edu.uniquindio.auth_service.messaging;

import co.edu.uniquindio.auth_service.model.Account;
import co.edu.uniquindio.auth_service.model.AccountStatus;
import co.edu.uniquindio.auth_service.model.ProcessedEvent;
import co.edu.uniquindio.auth_service.model.Role;
import co.edu.uniquindio.auth_service.repository.AccountRepository;
import co.edu.uniquindio.auth_service.repository.ProcessedEventRepository;
import co.edu.uniquindio.auth_service.security.JWTUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountLifecycleServiceTest {

    private AccountRepository accounts;
    private ProcessedEventRepository processedEvents;
    private JWTUtils jwtUtils;
    private AuthEventPublisher publisher;
    private AccountLifecycleService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        processedEvents = mock(ProcessedEventRepository.class);
        jwtUtils = mock(JWTUtils.class);
        publisher = mock(AuthEventPublisher.class);
        service = new AccountLifecycleService(accounts, processedEvents, jwtUtils, publisher);
    }

    private Account accountIn(AccountStatus status) {
        Account account = Account.builder()
                .id("E001").email("juan@empresa.com").password("hash").role(Role.USER).status(status).build();
        when(accounts.findById("E001")).thenReturn(Optional.of(account));
        return account;
    }

    // a) Caso borde: retirado durante vacaciones
    @Test
    void vacacionesFinalizadas_enCuentaDesactivadaPermanente_noCambiaEstadoNiPublica() {
        Account account = accountIn(AccountStatus.DESACTIVADA_PERMANENTE);

        service.process("evt-1", "vacaciones.finalizadas", "E001", null);

        assertEquals(AccountStatus.DESACTIVADA_PERMANENTE, account.getStatus());
        verify(accounts, never()).save(any());
        verifyNoInteractions(publisher);
    }

    // b)
    @Test
    void vacacionesIniciadas_enCuentaPendienteActivacion_noLaSuspende() {
        Account account = accountIn(AccountStatus.PENDIENTE_ACTIVACION);

        service.process("evt-2", "vacaciones.iniciadas", "E001", null);

        assertEquals(AccountStatus.PENDIENTE_ACTIVACION, account.getStatus());
        verify(accounts, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void vacacionesIniciadas_enCuentaActiva_laSuspendeYPublicaCuentaDesactivada() {
        Account account = accountIn(AccountStatus.ACTIVA);

        service.process("evt-3", "vacaciones.iniciadas", "E001", null);

        assertEquals(AccountStatus.SUSPENDIDA_TEMPORAL, account.getStatus());
        verify(accounts).save(account);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq("cuenta.desactivada"), data.capture());
        assertEquals("VACACIONES", data.getValue().get("motivo"));
        assertEquals(false, data.getValue().get("permanente"));
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    // c)
    @Test
    void vacacionesFinalizadas_desdeSuspendidaTemporal_reactivaYPublicaCuentaActivada() {
        Account account = accountIn(AccountStatus.SUSPENDIDA_TEMPORAL);

        service.process("evt-4", "vacaciones.finalizadas", "E001", null);

        assertEquals(AccountStatus.ACTIVA, account.getStatus());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq("cuenta.activada"), data.capture());
        assertEquals("FIN_VACACIONES", data.getValue().get("motivo"));
    }

    @Test
    void vacacionesFinalizadas_desdeOtrosEstados_noReactiva() {
        for (AccountStatus status : new AccountStatus[] {AccountStatus.ACTIVA, AccountStatus.PENDIENTE_ACTIVACION}) {
            Account account = accountIn(status);

            service.process("evt-5-" + status, "vacaciones.finalizadas", "E001", null);

            assertEquals(status, account.getStatus());
        }
        verify(accounts, never()).save(any());
        verifyNoInteractions(publisher);
    }

    // d)
    @Test
    void eventoDuplicado_noSeProcesaDosVeces() {
        Account account = accountIn(AccountStatus.ACTIVA);
        when(processedEvents.existsById("evt-dup")).thenReturn(false, true);

        service.process("evt-dup", "vacaciones.iniciadas", "E001", null);
        service.process("evt-dup", "vacaciones.iniciadas", "E001", null);

        assertEquals(AccountStatus.SUSPENDIDA_TEMPORAL, account.getStatus());
        verify(accounts).save(account);
        verify(publisher).publish(eq("cuenta.desactivada"), anyMap());
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void empleadoRetirado_siempreDesactivaPermanenteYPublicaMotivoRetiro() {
        Account account = accountIn(AccountStatus.SUSPENDIDA_TEMPORAL);

        service.process("evt-6", "empleado.retirado", "E001", null);

        assertEquals(AccountStatus.DESACTIVADA_PERMANENTE, account.getStatus());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq("cuenta.desactivada"), data.capture());
        assertEquals("RETIRO", data.getValue().get("motivo"));
        assertEquals(true, data.getValue().get("permanente"));
    }

    @Test
    void eventosDeEmpleadoSinCuenta_seConfirmanSinPublicar() {
        when(accounts.findById("E404")).thenReturn(Optional.empty());

        service.process("evt-7", "empleado.retirado", "E404", null);
        service.process("evt-8", "vacaciones.iniciadas", "E404", null);
        service.process("evt-9", "vacaciones.finalizadas", "E404", null);

        verify(accounts, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void empleadoCreado_creaCuentaPendienteSinPasswordYPublicaUsuarioCreado() {
        when(accounts.existsById("E010")).thenReturn(false);
        when(accounts.findByEmail("ana@empresa.com")).thenReturn(Optional.empty());
        when(jwtUtils.generateToken(eq("E010"), anyMap(), anyLong())).thenReturn("token-activacion");

        service.process("evt-10", "empleado.creado", "E010", "ana@empresa.com");

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertEquals("E010", saved.getValue().getId());
        assertEquals(Role.USER, saved.getValue().getRole());
        assertEquals(AccountStatus.PENDIENTE_ACTIVACION, saved.getValue().getStatus());
        assertNull(saved.getValue().getPassword());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq("usuario.creado"), data.capture());
        assertEquals("token-activacion", data.getValue().get("tokenActivacion"));
        assertEquals("ana@empresa.com", data.getValue().get("email"));
        assertFalse(data.getValue().containsKey("password"));
        assertTrue(data.getValue().keySet().stream().noneMatch(k -> k.toLowerCase().contains("contrase")));
    }

    @Test
    void empleadoCreado_siYaExisteLaCuenta_noLaRecreaNiRepublica() {
        when(accounts.existsById("E001")).thenReturn(true);

        service.process("evt-11", "empleado.creado", "E001", "juan@empresa.com");

        verify(accounts, never()).save(any());
        verify(publisher, never()).publish(anyString(), anyMap());
    }

    @Test
    void eventoFueraDelCicloDeVida_seIgnoraSinRegistrarlo() {
        service.process("evt-12", "empleado.actualizado", "E001", null);

        verifyNoInteractions(publisher);
        verify(processedEvents, never()).save(any());
    }
}
