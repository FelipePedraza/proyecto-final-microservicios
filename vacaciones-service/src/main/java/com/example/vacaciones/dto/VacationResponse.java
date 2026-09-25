package com.example.vacaciones.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.example.vacaciones.domain.Vacation;
import com.example.vacaciones.domain.VacationStatus;

public record VacationResponse(
        String id,
        String empleadoId,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        VacationStatus estado,
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
