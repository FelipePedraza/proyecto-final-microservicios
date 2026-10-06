package co.edu.uniquindio.auth_service.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ValidationErrorResponse", description = "Respuesta estándar cuando uno o más campos no pasan la validación.")
public record ValidationErrorResponseDTO(
        @Schema(description = "Indica que ocurrió un error.", example = "true")
        boolean error,
        @Schema(description = "Errores de validación por campo.")
        List<ValidationDTO> respuesta
) {
}
