package co.edu.uniquindio.auth_service.dtos;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordDTO(
    @NotBlank(message = "La contraseña actual es obligatoria")
    String oldPassword,

    @NotBlank(message = "La nueva contraseña es obligatoria")
    String newPassword
) {}
