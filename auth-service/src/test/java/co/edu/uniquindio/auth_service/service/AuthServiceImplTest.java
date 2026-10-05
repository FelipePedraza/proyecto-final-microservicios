package co.edu.uniquindio.auth_service.service;

import co.edu.uniquindio.auth_service.dtos.ChangePasswordDTO;
import co.edu.uniquindio.auth_service.dtos.ResetPasswordDTO;
import co.edu.uniquindio.auth_service.messaging.AuthEventPayloads;
import co.edu.uniquindio.auth_service.messaging.AuthEventPublisher;
import co.edu.uniquindio.auth_service.model.Account;
import co.edu.uniquindio.auth_service.model.AccountStatus;
import co.edu.uniquindio.auth_service.model.Role;
import co.edu.uniquindio.auth_service.repository.AccountRepository;
import co.edu.uniquindio.auth_service.security.JWTUtils;
import co.edu.uniquindio.auth_service.service.impl.AuthServiceImpl;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private AccountRepository accounts;
    private JWTUtils jwt;
    private AuthEventPublisher publisher;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        accounts = mock(AccountRepository.class);
        jwt = mock(JWTUtils.class);
        publisher = mock(AuthEventPublisher.class);
        service = new AuthServiceImpl(accounts, new BCryptPasswordEncoder(), jwt, publisher);
        when(jwt.validateAndGetSubject("tok", "RESET_PASSWORD")).thenReturn("E001");
    }

    private Account cuenta(AccountStatus status) {
        Account account = Account.builder()
                .id("E001").email("e1@empresa.com").password(null).role(Role.USER).status(status).build();
        when(accounts.findById("E001")).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    void primeraActivacionPasaAActivaYPublicaCuentaActivada() throws Exception {
        Account account = cuenta(AccountStatus.PENDIENTE_ACTIVACION);

        service.resetPassword(new ResetPasswordDTO("tok", "Clave12345"));

        assertEquals(AccountStatus.ACTIVA, account.getStatus());
        verify(publisher).publish(eq(AuthEventPayloads.CUENTA_ACTIVADA), anyMap());
    }

    @Test
    void recuperarContrasenaNoReactivaUnaCuentaSuspendida() throws Exception {
        Account account = cuenta(AccountStatus.SUSPENDIDA_TEMPORAL);

        service.resetPassword(new ResetPasswordDTO("tok", "Clave12345"));

        assertEquals(AccountStatus.SUSPENDIDA_TEMPORAL, account.getStatus());
        assertNotEquals(null, account.getPassword());
        verify(publisher, never()).publish(any(), anyMap());
    }

    @Test
    void recuperarContrasenaEnCuentaActivaNoPublicaCuentaActivada() throws Exception {
        Account account = cuenta(AccountStatus.ACTIVA);

        service.resetPassword(new ResetPasswordDTO("tok", "Clave12345"));

        assertEquals(AccountStatus.ACTIVA, account.getStatus());
        verify(publisher, never()).publish(any(), anyMap());
    }

    @Test
    void laPoliticaDeContrasenaSeAplicaAlCambioYAlReset() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertTrue(validator.validate(new ChangePasswordDTO("vieja", "Clave12345")).isEmpty());
        assertFalse(validator.validate(new ChangePasswordDTO("vieja", "corta1")).isEmpty());
        assertFalse(validator.validate(new ChangePasswordDTO("vieja", "sinnumeros")).isEmpty());
        assertFalse(validator.validate(new ChangePasswordDTO("vieja", "12345678")).isEmpty());
        assertTrue(validator.validate(new ResetPasswordDTO("tok", "Clave12345")).isEmpty());
        assertFalse(validator.validate(new ResetPasswordDTO("tok", "corta1")).isEmpty());
    }
}
