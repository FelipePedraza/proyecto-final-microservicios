package com.example.vacaciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.vacaciones.domain.Vacation;
import com.example.vacaciones.domain.VacationStatus;
import com.example.vacaciones.exception.VacationStateException;
import com.example.vacaciones.messaging.EmployeeEventConsumer;
import com.example.vacaciones.messaging.EmployeeReplica;
import com.example.vacaciones.messaging.EmployeeReplicaRepository;
import com.example.vacaciones.messaging.ProcessedEventRepository;
import com.example.vacaciones.messaging.VacationLifecycleEvent;
import com.example.vacaciones.repository.VacationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class VacationSchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-05T12:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private VacationRepository repository;
    @Mock
    private EmployeeReplicaRepository employees;
    @Mock
    private ProcessedEventRepository processedEvents;
    @Mock
    private ApplicationEventPublisher publisher;

    private VacationService service;

    @BeforeEach
    void setUp() {
        service = new VacationService(repository, employees, CLOCK, publisher);
    }

    @Test
    void advancesScheduledAndExpiredVacationsAndPublishesTypedEvents() {
        Vacation starting = vacation("V-START", "E001", TODAY, TODAY, VacationStatus.PROGRAMADA);
        Vacation ending = vacation("V-END", "E001", TODAY.minusDays(4), TODAY.minusDays(1), VacationStatus.EN_CURSO);
        when(repository.findByStatusAndStartDateLessThanEqual(VacationStatus.PROGRAMADA, TODAY))
                .thenReturn(List.of(starting));
        when(repository.findByStatusAndEndDateBefore(VacationStatus.EN_CURSO, TODAY))
                .thenReturn(List.of(ending));

        int transitions = service.advanceDueVacations();

        assertEquals(2, transitions);
        assertEquals(VacationStatus.EN_CURSO, starting.getStatus());
        assertEquals(VacationStatus.FINALIZADA, ending.getStatus());
        ArgumentCaptor<VacationLifecycleEvent> events = ArgumentCaptor.forClass(VacationLifecycleEvent.class);
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.times(2)).publishEvent(events.capture());
        assertEquals("vacaciones.iniciadas", events.getAllValues().get(0).type());
        assertEquals("V-START", events.getAllValues().get(0).vacationId());
        assertEquals("vacaciones.finalizadas", events.getAllValues().get(1).type());
        assertEquals("V-END", events.getAllValues().get(1).vacationId());
        verify(repository).findByStatusAndEndDateBefore(VacationStatus.EN_CURSO, TODAY);
    }

    @Test
    void finalizesInProgressVacationAfterRetirementWithoutReactivatingEmployee() throws Exception {
        Vacation scheduled = vacation("V-SCHEDULED", "E002", TODAY, TODAY.plusDays(1), VacationStatus.PROGRAMADA);
        Vacation inProgress = vacation("V-RETIRED", "E002", TODAY.minusDays(2), TODAY, VacationStatus.EN_CURSO);
        LocalDate tomorrow = TODAY.plusDays(1);
        EmployeeReplica employee = new EmployeeReplica(
                "E002", "Maria", "maria.lopez@empresa.com", "ACTIVO", Instant.now(CLOCK));
        service = new VacationService(
                repository,
                employees,
                Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC),
                publisher
        );
        when(processedEvents.existsById("retire-event")).thenReturn(false);
        when(employees.findById("E002")).thenReturn(Optional.of(employee));
        when(repository.findByEmployeeIdAndStatus("E002", VacationStatus.PROGRAMADA))
                .thenReturn(List.of(scheduled));

        EmployeeEventConsumer consumer = new EmployeeEventConsumer(
                new ObjectMapper(), processedEvents, employees, service, CLOCK);
        consumer.consume("""
                {"id":"retire-event","type":"empleado.retirado","data":{"empleadoId":"E002"}}
                """);

        assertEquals("RETIRADO", employee.getStatus());
        assertEquals(VacationStatus.CANCELADA, scheduled.getStatus());
        assertEquals(VacationStatus.EN_CURSO, inProgress.getStatus());

        when(repository.findByStatusAndStartDateLessThanEqual(VacationStatus.PROGRAMADA, tomorrow))
                .thenReturn(List.of());
        when(repository.findByStatusAndEndDateBefore(VacationStatus.EN_CURSO, tomorrow))
                .thenReturn(List.of(inProgress));

        assertEquals(1, service.advanceDueVacations());
        assertEquals(VacationStatus.FINALIZADA, inProgress.getStatus());
        ArgumentCaptor<VacationLifecycleEvent> event = ArgumentCaptor.forClass(VacationLifecycleEvent.class);
        org.mockito.Mockito.verify(publisher).publishEvent(event.capture());
        assertEquals("vacaciones.finalizadas", event.getValue().type());
        assertEquals("E002", event.getValue().employeeId());
        verify(repository).findByStatusAndEndDateBefore(VacationStatus.EN_CURSO, tomorrow);
    }

    @Test
    void finalizesOnTheDayAfterItsInclusiveEndDate() {
        Vacation ending = vacation(
                "V-TODAY-END",
                "E001",
                TODAY.minusDays(3),
                TODAY,
                VacationStatus.EN_CURSO
        );
        when(repository.findByStatusAndStartDateLessThanEqual(VacationStatus.PROGRAMADA, TODAY))
                .thenReturn(List.of());
        when(repository.findByStatusAndEndDateBefore(VacationStatus.EN_CURSO, TODAY))
                .thenReturn(List.of());

        service.advanceDueVacations();

        assertEquals(VacationStatus.EN_CURSO, ending.getStatus());
        verify(repository).findByStatusAndEndDateBefore(VacationStatus.EN_CURSO, TODAY);
    }

    @Test
    void forceFinishRejectsVacationNotInProgress() {
        Vacation scheduled = vacation("V-PLANNED", "E001", TODAY, TODAY, VacationStatus.PROGRAMADA);
        when(repository.findById("V-PLANNED")).thenReturn(java.util.Optional.of(scheduled));

        org.junit.jupiter.api.Assertions.assertThrows(
                VacationStateException.class,
                () -> service.forceFinish("V-PLANNED")
        );
    }

    private Vacation vacation(
            String id,
            String employeeId,
            LocalDate startDate,
            LocalDate endDate,
            VacationStatus status
    ) {
        Vacation vacation = new Vacation(id, employeeId, startDate, endDate, Instant.now(CLOCK));
        switch (status) {
            case EN_CURSO -> vacation.start();
            case FINALIZADA -> {
                vacation.start();
                vacation.finish();
            }
            case CANCELADA -> vacation.cancel();
            case PROGRAMADA -> {
            }
        }
        return vacation;
    }
}
