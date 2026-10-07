package com.example.vacaciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.example.vacaciones.domain.Vacation;
import com.example.vacaciones.domain.VacationStatus;
import com.example.vacaciones.dto.VacationResponse;
import com.example.vacaciones.exception.VacationNotFoundException;
import com.example.vacaciones.exception.VacationStateException;
import com.example.vacaciones.messaging.EmployeeReplicaRepository;
import com.example.vacaciones.messaging.VacationLifecycleEvent;
import com.example.vacaciones.repository.VacationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Casos borde del ciclo de vida: cancelar, cancelación masiva por retiro, inicio/fin forzados
 * y consultas. El avance automático (scheduler) está en {@link VacationSchedulerTest}.
 */
@ExtendWith(MockitoExtension.class)
class VacationServiceLifecycleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private VacationRepository repository;
    @Mock
    private EmployeeReplicaRepository employees;
    @Mock
    private ApplicationEventPublisher publisher;

    private VacationService service;

    @BeforeEach
    void setUp() {
        service = new VacationService(repository, employees, CLOCK, publisher);
    }

    // ---- cancel -------------------------------------------------------------------------------

    @Test
    void cancelsScheduledVacation() {
        Vacation scheduled = vacation("V-1", VacationStatus.PROGRAMADA);
        when(repository.findById("V-1")).thenReturn(Optional.of(scheduled));

        VacationResponse response = service.cancel("V-1");

        assertEquals(VacationStatus.CANCELADA, response.estado());
        assertEquals(VacationStatus.CANCELADA, scheduled.getStatus());
        verifyNoInteractions(publisher);
    }

    @Test
    void cancelRejectsVacationInProgressFinishedOrAlreadyCancelled() {
        for (VacationStatus status : List.of(
                VacationStatus.EN_CURSO, VacationStatus.FINALIZADA, VacationStatus.CANCELADA)) {
            Vacation vacation = vacation("V-" + status, status);
            when(repository.findById("V-" + status)).thenReturn(Optional.of(vacation));

            assertThrows(VacationStateException.class, () -> service.cancel("V-" + status));
            assertEquals(status, vacation.getStatus());
        }
    }

    @Test
    void cancelFailsWhenVacationDoesNotExist() {
        when(repository.findById("V-NONE")).thenReturn(Optional.empty());

        assertThrows(VacationNotFoundException.class, () -> service.cancel("V-NONE"));
    }

    // ---- cancelScheduledByEmployee (retiro) -----------------------------------------------------

    @Test
    void cancelsEveryScheduledVacationOfARetiredEmployeeAndReturnsTheCount() {
        Vacation a = vacation("V-A", VacationStatus.PROGRAMADA);
        Vacation b = vacation("V-B", VacationStatus.PROGRAMADA);
        when(repository.findByEmployeeIdAndStatus("E001", VacationStatus.PROGRAMADA)).thenReturn(List.of(a, b));

        assertEquals(2, service.cancelScheduledByEmployee("E001"));

        assertEquals(VacationStatus.CANCELADA, a.getStatus());
        assertEquals(VacationStatus.CANCELADA, b.getStatus());
    }

    @Test
    void returnsZeroWhenRetiredEmployeeHasNoScheduledVacations() {
        when(repository.findByEmployeeIdAndStatus("E001", VacationStatus.PROGRAMADA)).thenReturn(List.of());

        assertEquals(0, service.cancelScheduledByEmployee("E001"));
        verify(repository, never()).save(any());
    }

    // ---- forceStart / forceFinish -----------------------------------------------------------------

    @Test
    void forceStartMovesScheduledToInProgressAndPublishesStartedEvent() {
        Vacation scheduled = vacation("V-1", VacationStatus.PROGRAMADA);
        when(repository.findById("V-1")).thenReturn(Optional.of(scheduled));

        VacationResponse response = service.forceStart("V-1");

        assertEquals(VacationStatus.EN_CURSO, response.estado());
        assertEquals("vacaciones.iniciadas", publishedEvent().type());
    }

    @Test
    void forceStartRejectsAnythingButScheduled() {
        for (VacationStatus status : List.of(
                VacationStatus.EN_CURSO, VacationStatus.FINALIZADA, VacationStatus.CANCELADA)) {
            Vacation vacation = vacation("V-" + status, status);
            when(repository.findById("V-" + status)).thenReturn(Optional.of(vacation));

            assertThrows(VacationStateException.class, () -> service.forceStart("V-" + status));
        }
        verifyNoInteractions(publisher);
    }

    @Test
    void forceStartFailsWhenVacationDoesNotExist() {
        when(repository.findById("V-NONE")).thenReturn(Optional.empty());

        assertThrows(VacationNotFoundException.class, () -> service.forceStart("V-NONE"));
    }

    @Test
    void forceFinishMovesInProgressToFinishedAndPublishesFinishedEvent() {
        Vacation inProgress = vacation("V-1", VacationStatus.EN_CURSO);
        when(repository.findById("V-1")).thenReturn(Optional.of(inProgress));

        VacationResponse response = service.forceFinish("V-1");

        assertEquals(VacationStatus.FINALIZADA, response.estado());
        assertEquals("vacaciones.finalizadas", publishedEvent().type());
    }

    @Test
    void forceFinishRejectsScheduledFinishedAndCancelled() {
        for (VacationStatus status : List.of(
                VacationStatus.PROGRAMADA, VacationStatus.FINALIZADA, VacationStatus.CANCELADA)) {
            Vacation vacation = vacation("V-" + status, status);
            when(repository.findById("V-" + status)).thenReturn(Optional.of(vacation));

            assertThrows(VacationStateException.class, () -> service.forceFinish("V-" + status));
        }
        verifyNoInteractions(publisher);
    }

    @Test
    void forceFinishFailsWhenVacationDoesNotExist() {
        when(repository.findById("V-NONE")).thenReturn(Optional.empty());

        assertThrows(VacationNotFoundException.class, () -> service.forceFinish("V-NONE"));
    }

    // ---- scheduler: caso vacío ----------------------------------------------------------------

    @Test
    void advanceDueVacationsDoesNothingWhenNothingIsDue() {
        when(repository.findByStatusAndStartDateLessThanEqual(VacationStatus.PROGRAMADA, TODAY))
                .thenReturn(List.of());
        when(repository.findByStatusAndEndDateBefore(VacationStatus.EN_CURSO, TODAY)).thenReturn(List.of());

        assertEquals(0, service.advanceDueVacations());
        verifyNoInteractions(publisher);
    }

    // ---- get / list ---------------------------------------------------------------------------

    @Test
    void getReturnsTheVacation() {
        when(repository.findById("V-1")).thenReturn(Optional.of(vacation("V-1", VacationStatus.PROGRAMADA)));

        VacationResponse response = service.get("V-1");

        assertEquals("V-1", response.id());
        assertEquals("E001", response.empleadoId());
    }

    @Test
    void getFailsWhenVacationDoesNotExist() {
        when(repository.findById("V-NONE")).thenReturn(Optional.empty());

        assertThrows(VacationNotFoundException.class, () -> service.get("V-NONE"));
    }

    @Test
    void listWithoutEmployeeReturnsEverything() {
        when(repository.findAll()).thenReturn(List.of(
                vacation("V-1", VacationStatus.PROGRAMADA), vacation("V-2", VacationStatus.CANCELADA)));

        assertEquals(2, service.list(null).size());
        verify(repository, never()).findByEmployeeIdOrderByStartDateAsc(any());
    }

    @Test
    void listWithEmployeeFiltersByThatEmployee() {
        when(repository.findByEmployeeIdOrderByStartDateAsc("E001"))
                .thenReturn(List.of(vacation("V-1", VacationStatus.PROGRAMADA)));

        List<VacationResponse> result = service.list("E001");

        assertEquals(1, result.size());
        assertTrue(result.stream().allMatch(v -> v.empleadoId().equals("E001")));
        verify(repository, never()).findAll();
    }

    @Test
    void listReturnsEmptyListWhenEmployeeHasNoVacations() {
        when(repository.findByEmployeeIdOrderByStartDateAsc("E999")).thenReturn(List.of());

        assertTrue(service.list("E999").isEmpty());
    }

    // ---- utilidades ---------------------------------------------------------------------------

    private VacationLifecycleEvent publishedEvent() {
        ArgumentCaptor<VacationLifecycleEvent> event = ArgumentCaptor.forClass(VacationLifecycleEvent.class);
        verify(publisher).publishEvent(event.capture());
        return event.getValue();
    }

    private static Vacation vacation(String id, VacationStatus status) {
        Vacation vacation = new Vacation(id, "E001", TODAY.plusDays(5), TODAY.plusDays(9), Instant.now(CLOCK));
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
