package com.example.vacaciones.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Máquina de estados de {@link Vacation}: PROGRAMADA → EN_CURSO → FINALIZADA, o PROGRAMADA → CANCELADA. */
class VacationTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 21);
    private static final LocalDate END = LocalDate.of(2026, 12, 28);

    @Test
    void startsAsScheduled() {
        assertEquals(VacationStatus.PROGRAMADA, newVacation().getStatus());
    }

    @Test
    void exposesTheDataItWasCreatedWith() {
        Instant createdAt = Instant.parse("2026-10-05T12:00:00Z");
        Vacation vacation = new Vacation("V-2026-0001", "E001", START, END, createdAt);

        assertEquals("V-2026-0001", vacation.getId());
        assertEquals("E001", vacation.getEmployeeId());
        assertEquals(START, vacation.getStartDate());
        assertEquals(END, vacation.getEndDate());
        assertEquals(createdAt, vacation.getCreatedAt());
    }

    @Test
    void followsTheHappyPathScheduledInProgressFinished() {
        Vacation vacation = newVacation();

        vacation.start();
        assertEquals(VacationStatus.EN_CURSO, vacation.getStatus());

        vacation.finish();
        assertEquals(VacationStatus.FINALIZADA, vacation.getStatus());
    }

    @Test
    void canBeCancelledOnlyWhileScheduled() {
        Vacation vacation = newVacation();
        vacation.cancel();
        assertEquals(VacationStatus.CANCELADA, vacation.getStatus());

        assertThrows(IllegalStateException.class, vacation::cancel);
    }

    @Test
    void cannotBeCancelledOnceInProgressOrFinished() {
        Vacation inProgress = newVacation();
        inProgress.start();
        assertThrows(IllegalStateException.class, inProgress::cancel);

        Vacation finished = newVacation();
        finished.start();
        finished.finish();
        assertThrows(IllegalStateException.class, finished::cancel);
    }

    @Test
    void cannotStartTwiceNorFromCancelledOrFinished() {
        Vacation started = newVacation();
        started.start();
        assertThrows(IllegalStateException.class, started::start);

        Vacation cancelled = newVacation();
        cancelled.cancel();
        assertThrows(IllegalStateException.class, cancelled::start);

        Vacation finished = newVacation();
        finished.start();
        finished.finish();
        assertThrows(IllegalStateException.class, finished::start);
    }

    @Test
    void cannotFinishUnlessInProgress() {
        assertThrows(IllegalStateException.class, newVacation()::finish);

        Vacation cancelled = newVacation();
        cancelled.cancel();
        assertThrows(IllegalStateException.class, cancelled::finish);

        Vacation finished = newVacation();
        finished.start();
        finished.finish();
        assertThrows(IllegalStateException.class, finished::finish);
    }

    // ---- cálculo de días (KAN-15) ------------------------------------------------------------------

    @Test
    void singleDayVacationCountsAsOneDay() {
        assertEquals(1, vacation(LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 21)).totalDays());
    }

    @Test
    void countsBothEndpointsInclusively() {
        assertEquals(8, vacation(START, END).totalDays());
        assertEquals(2, vacation(START, START.plusDays(1)).totalDays());
    }

    @Test
    void countsAcrossMonthAndYearBoundaries() {
        assertEquals(10, vacation(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 6)).totalDays());
        assertEquals(31, vacation(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).totalDays());
    }

    @Test
    void countsLeapDayOnlyInLeapYears() {
        assertEquals(3, vacation(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 3, 1)).totalDays());
        assertEquals(2, vacation(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 1)).totalDays());
    }

    @Test
    void countsAFullYear() {
        assertEquals(365, vacation(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)).totalDays());
        assertEquals(366, vacation(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31)).totalDays());
    }

    @Test
    void totalDaysDoesNotDependOnTheStatus() {
        Vacation vacation = newVacation();
        long days = vacation.totalDays();
        vacation.start();
        vacation.finish();

        assertEquals(days, vacation.totalDays());
    }

    private static Vacation vacation(LocalDate start, LocalDate end) {
        return new Vacation("V-2026-0001", "E001", start, end, Instant.parse("2026-10-05T12:00:00Z"));
    }

    private static Vacation newVacation() {
        return new Vacation("V-2026-0001", "E001", START, END, Instant.parse("2026-10-05T12:00:00Z"));
    }
}
