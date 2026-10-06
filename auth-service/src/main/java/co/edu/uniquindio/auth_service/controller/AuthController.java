package co.edu.uniquindio.auth_service.controller;

import co.edu.uniquindio.auth_service.dtos.*;
import co.edu.uniquindio.auth_service.service.AuthService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Operación pública. Devuelve un JWT de acceso válido durante una hora.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa; devuelve el token.",
                    content = @Content(schema = @Schema(implementation = TokenDTO.class),
                            examples = @ExampleObject(value = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9...\"}"))),
            @ApiResponse(responseCode = "400", description = "El cuerpo no cumple las validaciones.",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":[{\"field\":\"email\",\"defaultMessage\":\"Debe tener un formato de correo válido\"}]}"))),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":\"Credenciales incorrectas\"}"))),
            @ApiResponse(responseCode = "403", description = "La cuenta no está activa.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":\"La cuenta no está activa\"}"))),
            @ApiResponse(responseCode = "500", description = "Error interno no esperado.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":\"Error interno\"}")))
    })
    public ResponseEntity<TokenDTO> login(@Valid @RequestBody LoginDTO loginDTO) throws Exception {
        TokenDTO token = authService.login(loginDTO);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/recover-password")
    @Operation(summary = "Solicitar recuperación de contraseña",
            description = "Operación pública. Siempre responde con el mismo mensaje, exista o no una cuenta para el correo, para evitar revelar su existencia.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud procesada sin revelar si el correo existe.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":false,\"respuesta\":\"Si el correo existe, se han enviado las instrucciones de recuperación.\"}"))),
            @ApiResponse(responseCode = "400", description = "El cuerpo no cumple las validaciones.",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":[{\"field\":\"email\",\"defaultMessage\":\"El email es obligatorio\"}]}"))),
            @ApiResponse(responseCode = "500", description = "Error interno no esperado.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":\"Error interno\"}")))
    })
    public ResponseEntity<ResponseDTO<String>> recoverPassword(@Valid @RequestBody RecoverPasswordDTO recoverDTO) throws Exception {
        authService.recoverPassword(recoverDTO);
        // NOTA: Se retorna OK exista o no el correo por razones de seguridad
        return ResponseEntity.ok(new ResponseDTO(false, "Si el correo existe, se han enviado las instrucciones de recuperación."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Restablecer contraseña", description = "Operación pública. Requiere el token temporal enviado en el flujo de recuperación; el token expira en 15 minutos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contraseña actualizada.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":false,\"respuesta\":\"Contraseña actualizada exitosamente.\"}"))),
            @ApiResponse(responseCode = "400", description = "El cuerpo no cumple las validaciones.",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":[{\"field\":\"newPassword\",\"defaultMessage\":\"La contraseña debe tener entre 8 y 72 caracteres\"}]}"))),
            @ApiResponse(responseCode = "401", description = "Token de recuperación inválido, expirado o de tipo incorrecto.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":\"El token de recuperación es inválido o ha expirado.\"}"))),
            @ApiResponse(responseCode = "403", description = "La cuenta está desactivada permanentemente.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":\"La cuenta está desactivada permanentemente.\"}"))),
            @ApiResponse(responseCode = "404", description = "No existe la cuenta asociada al token.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":\"Cuenta no encontrada\"}"))),
            @ApiResponse(responseCode = "500", description = "Error interno no esperado.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":\"Error interno\"}")))
    })
    public ResponseEntity<ResponseDTO<String>> resetPassword(@Valid @RequestBody ResetPasswordDTO resetDTO) throws Exception {
        authService.resetPassword(resetDTO);
        return ResponseEntity.ok(new ResponseDTO(false, "Contraseña actualizada exitosamente."));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Cambiar contraseña",
            description = "Requiere un JWT de acceso válido. En el flujo público, el gateway valida el JWT y agrega X-User-Id con el identificador del usuario autenticado. El servicio de autenticación confía en ese encabezado interno.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contraseña cambiada.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":false,\"respuesta\":\"Contraseña cambiada exitosamente.\"}"))),
            @ApiResponse(responseCode = "400", description = "El cuerpo no cumple las validaciones o la contraseña actual es incorrecta.",
                    content = @Content(schema = @Schema(oneOf = {ValidationErrorResponseDTO.class, ResponseDTO.class}),
                            examples = {
                                    @ExampleObject(name = "validation", value = "{\"error\":true,\"respuesta\":[{\"field\":\"newPassword\",\"defaultMessage\":\"La contraseña debe tener entre 8 y 72 caracteres\"}]}"),
                                    @ExampleObject(name = "incorrectCurrentPassword", value = "{\"error\":true,\"respuesta\":\"La contraseña actual es incorrecta\"}")
                            })),
            @ApiResponse(responseCode = "401", description = "Falta el JWT o no es válido; respuesta del gateway."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene acceso; respuesta del gateway."),
            @ApiResponse(responseCode = "404", description = "No existe la cuenta identificada por X-User-Id.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":\"Cuenta no encontrada\"}"))),
            @ApiResponse(responseCode = "500", description = "Error interno no esperado.",
                    content = @Content(schema = @Schema(implementation = ResponseDTO.class),
                            examples = @ExampleObject(value = "{\"error\":true,\"respuesta\":\"Error interno\"}")))
    })
    public ResponseEntity<ResponseDTO<String>> changePassword(
            @RequestHeader(value = "X-User-Id", required = true)
            @io.swagger.v3.oas.annotations.Parameter(description = "Identificador de usuario inyectado por el gateway después de validar el JWT.", example = "550e8400-e29b-41d4-a716-446655440000")
            String userId,
            @Valid @RequestBody ChangePasswordDTO changeDTO) throws Exception {
        
        authService.changePassword(userId, changeDTO);
        return ResponseEntity.ok(new ResponseDTO(false, "Contraseña cambiada exitosamente."));
    }
}
