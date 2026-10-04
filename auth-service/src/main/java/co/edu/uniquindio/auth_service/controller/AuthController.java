package co.edu.uniquindio.auth_service.controller;

import co.edu.uniquindio.auth_service.dtos.*;
import co.edu.uniquindio.auth_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<TokenDTO> login(@Valid @RequestBody LoginDTO loginDTO) throws Exception {
        TokenDTO token = authService.login(loginDTO);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/recover-password")
    public ResponseEntity<ResponseDTO> recoverPassword(@Valid @RequestBody RecoverPasswordDTO recoverDTO) throws Exception {
        authService.recoverPassword(recoverDTO);
        // NOTA: Se retorna OK exista o no el correo por razones de seguridad
        return ResponseEntity.ok(new ResponseDTO(false, "Si el correo existe, se han enviado las instrucciones de recuperación."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ResponseDTO> resetPassword(@Valid @RequestBody ResetPasswordDTO resetDTO) throws Exception {
        authService.resetPassword(resetDTO);
        return ResponseEntity.ok(new ResponseDTO(false, "Contraseña actualizada exitosamente."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ResponseDTO> changePassword(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody ChangePasswordDTO changeDTO) throws Exception {
        
        authService.changePassword(userId, changeDTO);
        return ResponseEntity.ok(new ResponseDTO(false, "Contraseña cambiada exitosamente."));
    }
}
