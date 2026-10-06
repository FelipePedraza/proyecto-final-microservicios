package co.edu.uniquindio.auth_service.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Correo de la cuenta para iniciar recuperación.")
public record RecoverPasswordDTO(
    @Schema(description = "Correo registrado. La respuesta no confirma si existe.", example = "usuario@empresa.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Debe tener un formato de correo válido")
    String email
) {}
