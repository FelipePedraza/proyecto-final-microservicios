package com.example.vacaciones.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos necesarios para programar un período de vacaciones")
public record VacationRequest(
        @Schema(description = "Identificador de un empleado existente y no retirado",
                example = "E001", maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 100)
        String empleadoId,

        @Schema(description = "Primer día del período, inclusivo; debe ser hoy o posterior",
                example = "2026-12-21", format = "date", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @FutureOrPresent
        LocalDate fechaInicio,

        @Schema(description = "Último día del período, inclusivo; no puede preceder a fechaInicio",
                example = "2026-12-28", format = "date", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        LocalDate fechaFin
) {

    @Schema(hidden = true)
    @AssertTrue(message = "La fecha de fin no puede ser anterior a la fecha de inicio")
    public boolean isValidRange() {
        return fechaInicio != null
                && fechaFin != null
                && !fechaFin.isBefore(fechaInicio);
    }
}
