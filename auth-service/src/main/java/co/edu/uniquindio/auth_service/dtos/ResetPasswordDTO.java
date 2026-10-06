package co.edu.uniquindio.auth_service.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Token temporal de recuperación y nueva contraseña.")
public record ResetPasswordDTO(
    @Schema(description = "JWT temporal recibido por el canal de recuperación; expira en 15 minutos.", example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El token de recuperación es obligatorio")
    String resetToken,

    @Schema(description = "Entre 8 y 72 caracteres, con al menos una letra y un número.", example = "NuevaClave123", requiredMode = Schema.RequiredMode.REQUIRED, format = "password", minLength = 8, maxLength = 72)
    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "La contraseña debe contener al menos una letra y un número")
    String newPassword
) {}
