package co.edu.uniquindio.auth_service.service;

import co.edu.uniquindio.auth_service.dtos.ChangePasswordDTO;
import co.edu.uniquindio.auth_service.dtos.LoginDTO;
import co.edu.uniquindio.auth_service.dtos.RecoverPasswordDTO;
import co.edu.uniquindio.auth_service.dtos.ResetPasswordDTO;
import co.edu.uniquindio.auth_service.dtos.TokenDTO;
import co.edu.uniquindio.auth_service.exception.BadRequestException;
import co.edu.uniquindio.auth_service.exception.ForbiddenException;
import co.edu.uniquindio.auth_service.exception.InvalidTokenException;
import co.edu.uniquindio.auth_service.exception.ResourceNotFoundException;
import co.edu.uniquindio.auth_service.exception.UnauthorizedException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
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
    private BCryptPasswordEncoder passwordEncoder;


    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        jwt = mock(JWTUtils.class);
        publisher = mock(AuthEventPublisher.class);
        passwordEncoder = new BCryptPasswordEncoder();
        service = new AuthServiceImpl(accounts, passwordEncoder, jwt, publisher);
        when(jwt.validateAndGetSubject("tok", "RESET_PASSWORD")).thenReturn("E001");
    }

    private Account cuenta(AccountStatus status) {
        Account account = Account.builder()
                .id("E001").email("e1@empresa.com").password(null).role(Role.USER).status(status).build();
        when(accounts.findById("E001")).thenReturn(Optional.of(account));
        return account;
    }

    private Account cuentaConContrasena(AccountStatus status, String password) {
        return Account.builder()
                .id("E001")
                .email("e1@empresa.com")
                .password(passwordEncoder.encode(password))
                .role(Role.USER)
                .status(status)
                .build();
    }

    @Test
    void loginGeneraTokenParaCredencialesValidas() {
        Account account = cuentaConContrasena(AccountStatus.ACTIVA, "Clave12345");
        when(accounts.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(jwt.generateToken(eq("E001"), anyMap(), anyLong())).thenReturn("access-token");

        TokenDTO token = service.login(new LoginDTO(account.getEmail(), "Clave12345"));

        assertEquals("access-token", token.token());
    }

    @Test
    void loginRechazaCuentaInexistente() {
        LoginDTO credentials = new LoginDTO("missing@empresa.com", "Clave12345");
        when(accounts.findByEmail(credentials.email())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> service.login(credentials));
    }

    @Test
    void loginRechazaContrasenaIncorrectaOCuentaInactiva() {
        Account activeAccount = cuentaConContrasena(AccountStatus.ACTIVA, "Clave12345");
        when(accounts.findByEmail(activeAccount.getEmail())).thenReturn(Optional.of(activeAccount));
        LoginDTO wrongPassword = new LoginDTO(activeAccount.getEmail(), "OtraClave123");
        assertThrows(UnauthorizedException.class, () -> service.login(wrongPassword));

        Account suspendedAccount = cuentaConContrasena(AccountStatus.SUSPENDIDA_TEMPORAL, "Clave12345");
        when(accounts.findByEmail(suspendedAccount.getEmail())).thenReturn(Optional.of(suspendedAccount));
        LoginDTO validPassword = new LoginDTO(suspendedAccount.getEmail(), "Clave12345");
        assertThrows(ForbiddenException.class, () -> service.login(validPassword));
    }

    @Test
    void recuperarContrasenaNoPublicaParaCuentaInexistenteORetirada() {
        RecoverPasswordDTO recovery = new RecoverPasswordDTO("e1@empresa.com");
        when(accounts.findByEmail(recovery.email())).thenReturn(Optional.empty());
        service.recoverPassword(recovery);

        Account retired = cuentaConContrasena(AccountStatus.DESACTIVADA_PERMANENTE, "Clave12345");
        when(accounts.findByEmail(recovery.email())).thenReturn(Optional.of(retired));
        service.recoverPassword(recovery);

        verify(publisher, never()).publish(anyString(), anyMap());
    }

    @Test
    void recuperarContrasenaPublicaTokenParaCuentaActiva() {
        Account account = cuentaConContrasena(AccountStatus.ACTIVA, "Clave12345");
        when(accounts.findByEmail(account.getEmail())).thenReturn(Optional.of(account));

        service.recoverPassword(new RecoverPasswordDTO(account.getEmail()));

        verify(publisher).publish(eq(AuthEventPayloads.USUARIO_RECUPERACION), anyMap());
    }

    @Test
    void cambiarContrasenaValidaLaActualYGuardaLaNueva() {
        Account account = cuentaConContrasena(AccountStatus.ACTIVA, "ClaveActual123");
        when(accounts.findById("E001")).thenReturn(Optional.of(account));
        ChangePasswordDTO change = new ChangePasswordDTO("ClaveActual123", "ClaveNueva123");

        service.changePassword("E001", change);

        assertTrue(passwordEncoder.matches("ClaveNueva123", account.getPassword()));
        verify(accounts).save(account);
    }

    @Test
    void cambiarContrasenaRechazaClaveActualIncorrectaOCuentaInexistente() {
        Account account = cuentaConContrasena(AccountStatus.ACTIVA, "ClaveActual123");
        when(accounts.findById("E001")).thenReturn(Optional.of(account));
        ChangePasswordDTO wrongPassword = new ChangePasswordDTO("OtraClave123", "ClaveNueva123");
        assertThrows(BadRequestException.class, () -> service.changePassword("E001", wrongPassword));

        when(accounts.findById("missing")).thenReturn(Optional.empty());
        ChangePasswordDTO validPassword = new ChangePasswordDTO("ClaveActual123", "ClaveNueva123");
        assertThrows(ResourceNotFoundException.class, () -> service.changePassword("missing", validPassword));
    }

    @Test
    void resetRechazaTokenInvalidoYCuentasRetiradas() {
        ResetPasswordDTO invalidToken = new ResetPasswordDTO("invalid", "Clave12345");
        when(jwt.validateAndGetSubject("invalid", "RESET_PASSWORD"))
                .thenThrow(new InvalidTokenException("token inválido"));
        assertThrows(UnauthorizedException.class, () -> service.resetPassword(invalidToken));

        cuenta(AccountStatus.DESACTIVADA_PERMANENTE);
        ResetPasswordDTO retiredAccountReset = new ResetPasswordDTO("tok", "Clave12345");
        assertThrows(ForbiddenException.class,
                () -> service.resetPassword(retiredAccountReset));
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
