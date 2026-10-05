package com.example.vacaciones.messaging;

import java.time.LocalDate;

import com.example.vacaciones.domain.Vacation;

public record VacationLifecycleEvent(
        String type,
        String vacationId,
        String employeeId,
        LocalDate startDate,
        LocalDate endDate
) {

    public static VacationLifecycleEvent from(Vacation vacation, String type) {
        return new VacationLifecycleEvent(
                type,
                vacation.getId(),
                vacation.getEmployeeId(),
                vacation.getStartDate(),
                vacation.getEndDate()
        );
    }
}
