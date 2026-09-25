package com.example.vacaciones.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VacationRequest(
        @NotBlank
        @Size(max = 100)
        String empleadoId,

        @NotNull
        @FutureOrPresent
        LocalDate fechaInicio,

        @NotNull
        LocalDate fechaFin
) {

    @AssertTrue(message = "La fecha de fin debe ser posterior a la fecha de inicio")
    public boolean isValidRange() {
        return fechaInicio != null
                && fechaFin != null
                && fechaFin.isAfter(fechaInicio);
    }
}
