package com.example.vacaciones.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.example.vacaciones.domain.Vacation;
import com.example.vacaciones.domain.VacationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Período de vacaciones y su estado actual")
public record VacationResponse(
        @Schema(description = "Identificador generado para el período", example = "V-2026-0001")
        String id,
        @Schema(description = "Identificador del empleado", example = "E001")
        String empleadoId,
        @Schema(description = "Primer día del período, inclusivo", example = "2026-12-21", format = "date")
        LocalDate fechaInicio,
        @Schema(description = "Último día del período, inclusivo", example = "2026-12-28", format = "date")
        LocalDate fechaFin,
        @Schema(description = "Estado actual del período",
                allowableValues = {"PROGRAMADA", "EN_CURSO", "FINALIZADA", "CANCELADA"})
        VacationStatus estado,
        @Schema(description = "Instante UTC de creación", example = "2026-10-06T16:50:00Z", format = "date-time")
        Instant fechaCreacion
) {

    public static VacationResponse from(Vacation vacation) {
        return new VacationResponse(
                vacation.getId(),
                vacation.getEmployeeId(),
                vacation.getStartDate(),
                vacation.getEndDate(),
                vacation.getStatus(),
                vacation.getCreatedAt()
        );
    }
}
