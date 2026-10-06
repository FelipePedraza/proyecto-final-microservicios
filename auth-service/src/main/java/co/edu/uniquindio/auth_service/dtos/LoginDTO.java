package co.edu.uniquindio.auth_service.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciales de acceso.")
public record LoginDTO(
    @Schema(description = "Correo registrado en la cuenta.", example = "usuario@empresa.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Debe tener un formato de correo válido")
    String email,

    @Schema(description = "Contraseña de la cuenta.", example = "Clave1234", requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
    @NotBlank(message = "La contraseña es obligatoria")
    String password
) {}
