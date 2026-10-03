package co.edu.uniquindio.auth_service.dtos;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordDTO(
    @NotBlank(message = "El token de recuperación es obligatorio")
    String resetToken,

    @NotBlank(message = "La nueva contraseña es obligatoria")
    String newPassword
) {}
