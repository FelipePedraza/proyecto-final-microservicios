package com.example.vacaciones.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error de la API")
public record ApiErrorResponse(
        @Schema(description = "Código estable del error", example = "no_encontrada",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String error,

        @Schema(description = "Descripción legible del error", example = "La vacación no existe",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message,

        @Schema(description = "Período activo que causa el solapamiento; solo aparece con `error=solapamiento`",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        VacationResponse conflicto,

        @Schema(description = "Instante UTC en que se generó el error", type = "string", format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant timestamp
) {
}
