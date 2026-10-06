package co.edu.uniquindio.auth_service.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Envoltorio estándar de respuesta.")
public record ResponseDTO<T>(
    @Schema(description = "Indica si la respuesta representa un error.", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean error,
    @Schema(description = "Mensaje de resultado o detalle de error. En validaciones contiene una lista de objetos field/defaultMessage.", requiredMode = Schema.RequiredMode.REQUIRED)
    T respuesta
) {}
