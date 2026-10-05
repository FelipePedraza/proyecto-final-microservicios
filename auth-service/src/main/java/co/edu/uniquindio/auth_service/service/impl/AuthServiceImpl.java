package co.edu.uniquindio.auth_service.service.impl;

import co.edu.uniquindio.auth_service.dtos.*;
import co.edu.uniquindio.auth_service.exception.BadRequestException;
import co.edu.uniquindio.auth_service.exception.ForbiddenException;
import co.edu.uniquindio.auth_service.exception.ResourceNotFoundException;
import co.edu.uniquindio.auth_service.exception.UnauthorizedException;
import co.edu.uniquindio.auth_service.messaging.AuthEventPayloads;
import co.edu.uniquindio.auth_service.messaging.AuthEventPublisher;
import co.edu.uniquindio.auth_service.model.Account;
import co.edu.uniquindio.auth_service.model.AccountStatus;
import co.edu.uniquindio.auth_service.repository.AccountRepository;
import co.edu.uniquindio.auth_service.security.JWTUtils;
import co.edu.uniquindio.auth_service.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTUtils jwtUtils;
    private final AuthEventPublisher publisher;

    @Override
    public TokenDTO login(LoginDTO loginDTO) throws Exception {
        Account account = accountRepository.findByEmail(loginDTO.email())
                .orElseThrow(() -> new UnauthorizedException("Credenciales incorrectas"));

        // Validar contraseña
        if (account.getPassword() == null || !passwordEncoder.matches(loginDTO.password(), account.getPassword())) {
            throw new UnauthorizedException("Credenciales incorrectas");
        }

        // Validar estado de la cuenta
        if (account.getStatus() != AccountStatus.ACTIVA) {
            throw new ForbiddenException("La cuenta no está activa. Estado actual: " + account.getStatus());
        }

        // Generar Access Token (1 Hora)
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", account.getRole().name());
        
        String token = jwtUtils.generateToken(account.getId(), claims, 3600000); // 1 hora en ms

        return new TokenDTO(token);
    }

    @Override
    public void recoverPassword(RecoverPasswordDTO recoverDTO) throws Exception {
        Account account = accountRepository.findByEmail(recoverDTO.email()).orElse(null);
        
        // Por seguridad, si no existe o está retirada permanentemente, no hacemos nada (pero no lanzamos error)
        if (account != null && account.getStatus() != AccountStatus.DESACTIVADA_PERMANENTE) {
            
            // Generar Reset Token (15 minutos) con claim especial
            Map<String, Object> claims = new HashMap<>();
            claims.put("type", "RESET_PASSWORD");
            String resetToken = jwtUtils.generateToken(account.getId(), claims, 900000); // 15 min en ms

            publisher.publish(AuthEventPayloads.USUARIO_RECUPERACION,
                    AuthEventPayloads.usuarioRecuperacion(account.getEmail(), resetToken,
                            Instant.now().plusSeconds(900).toString()));
        }
    }

    @Override
    public void resetPassword(ResetPasswordDTO resetDTO) throws Exception {
        // Validar y extraer el subject (userId) del token
        String userId;
        try {
            userId = jwtUtils.validateAndGetSubject(resetDTO.resetToken(), "RESET_PASSWORD");
        } catch (Exception e) {
            throw new UnauthorizedException("El token de recuperación es inválido o ha expirado.");
        }

        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada"));

        if (account.getStatus() == AccountStatus.DESACTIVADA_PERMANENTE) {
            throw new ForbiddenException("La cuenta está desactivada permanentemente.");
        }

        // Solo la primera activación cambia el estado. Una cuenta ACTIVA o SUSPENDIDA_TEMPORAL
        // (recuperación de contraseña) conserva su estado: reactivarla aquí saltaría la suspensión.
        boolean primeraActivacion = account.getStatus() == AccountStatus.PENDIENTE_ACTIVACION;

        account.setPassword(passwordEncoder.encode(resetDTO.newPassword()));
        if (primeraActivacion) {
            account.setStatus(AccountStatus.ACTIVA);
        }
        accountRepository.save(account);

        if (primeraActivacion) {
            publisher.publish(AuthEventPayloads.CUENTA_ACTIVADA,
                    AuthEventPayloads.cuentaActivada(account.getId(), account.getEmail(), AuthEventPayloads.MOTIVO_ACTIVACION_INICIAL));
        }
    }

    @Override
    public void changePassword(String userId, ChangePasswordDTO changeDTO) throws Exception {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada"));

        // Validar password antigua
        if (account.getPassword() == null || !passwordEncoder.matches(changeDTO.oldPassword(), account.getPassword())) {
            throw new BadRequestException("La contraseña actual es incorrecta");
        }

        // Actualizar por la nueva
        account.setPassword(passwordEncoder.encode(changeDTO.newPassword()));
        accountRepository.save(account);
    }
}
