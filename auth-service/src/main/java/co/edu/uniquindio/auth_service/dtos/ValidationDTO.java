package co.edu.uniquindio.auth_service.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error de validación asociado a un campo.")
public record ValidationDTO(
    @Schema(description = "Nombre del campo que no pasó la validación.", example = "email")
    String field,
    @Schema(description = "Mensaje de validación.", example = "El email es obligatorio")
    String defaultMessage
) {}
