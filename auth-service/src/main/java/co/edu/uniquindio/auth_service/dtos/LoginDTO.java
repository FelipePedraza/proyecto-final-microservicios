package co.edu.uniquindio.auth_service.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginDTO(
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Debe tener un formato de correo válido")
    String email,

    @NotBlank(message = "La contraseña es obligatoria")
    String password
) {}
