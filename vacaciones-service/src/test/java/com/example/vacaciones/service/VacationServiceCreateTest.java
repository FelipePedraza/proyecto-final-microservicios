package com.example.vacaciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.example.vacaciones.dto.VacationRequest;
import com.example.vacaciones.dto.VacationResponse;
import com.example.vacaciones.exception.VacationConflictException;
import com.example.vacaciones.exception.VacationValidationException;
import com.example.vacaciones.messaging.EmployeeReplica;
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
 * Pruebas unitarias de {@link VacationService#create}: reglas de fecha, validación del empleado,
 * solapamiento y publicación del evento. Todas las dependencias están aisladas con mocks.
 */
@ExtendWith(MockitoExtension.class)
class VacationServiceCreateTest {

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

    // ---- camino feliz -------------------------------------------------------------------------

    @Test
    void createsScheduledVacationWithGeneratedIdAndPublishesProgrammedEvent() {
        givenEmployee("E001", "ACTIVO");
        givenNoOverlaps("E001", TODAY.plusDays(10), TODAY.plusDays(17));
        givenNextNumber(7);

        VacationResponse response = service.create(request("E001", TODAY.plusDays(10), TODAY.plusDays(17)));

        assertEquals("V-2026-0007", response.id());
        assertEquals("E001", response.empleadoId());
        assertEquals(TODAY.plusDays(10), response.fechaInicio());
        assertEquals(TODAY.plusDays(17), response.fechaFin());
        assertEquals(VacationStatus.PROGRAMADA, response.estado());
        assertEquals(Instant.now(CLOCK), response.fechaCreacion());

        ArgumentCaptor<VacationLifecycleEvent> event = ArgumentCaptor.forClass(VacationLifecycleEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals("vacaciones.programadas", event.getValue().type());
        assertEquals("V-2026-0007", event.getValue().vacationId());
        assertEquals("E001", event.getValue().employeeId());
        assertEquals(TODAY.plusDays(10), event.getValue().startDate());
        assertEquals(TODAY.plusDays(17), event.getValue().endDate());
    }

    @Test
    void padsSequenceNumberToFourDigits() {
        givenEmployee("E001", "ACTIVO");
        givenNoOverlaps("E001", TODAY, TODAY);
        givenNextNumber(12345);

        assertEquals("V-2026-12345", service.create(request("E001", TODAY, TODAY)).id());
    }

    @Test
    void trimsEmployeeIdBeforeLookingItUpAndPersisting() {
        givenEmployee("E001", "ACTIVO");
        givenNoOverlaps("E001", TODAY.plusDays(1), TODAY.plusDays(2));
        givenNextNumber(1);

        VacationResponse response = service.create(request("  E001  ", TODAY.plusDays(1), TODAY.plusDays(2)));

        assertEquals("E001", response.empleadoId());
        verify(employees).findById("E001");
    }

    // ---- reglas de fecha ----------------------------------------------------------------------

    @Test
    void acceptsStartDateEqualToToday() {
        givenEmployee("E001", "ACTIVO");
        givenNoOverlaps("E001", TODAY, TODAY.plusDays(2));
        givenNextNumber(1);

        assertEquals(TODAY, service.create(request("E001", TODAY, TODAY.plusDays(2))).fechaInicio());
    }

    @Test
    void acceptsSingleDayVacation() {
        LocalDate day = TODAY.plusDays(3);
        givenEmployee("E001", "ACTIVO");
        givenNoOverlaps("E001", day, day);
        givenNextNumber(1);

        VacationResponse response = service.create(request("E001", day, day));

        assertEquals(day, response.fechaInicio());
        assertEquals(day, response.fechaFin());
    }

    @Test
    void rejectsStartDateInThePastBeforeTouchingAnyDependency() {
        VacationValidationException error = assertThrows(
                VacationValidationException.class,
                () -> service.create(request("E001", TODAY.minusDays(1), TODAY.plusDays(3))));

        assertEquals("fecha_en_el_pasado", error.code);
        verifyNoInteractions(employees, repository, publisher);
    }

    // ---- validación del empleado --------------------------------------------------------------

    @Test
    void rejectsUnknownEmployee() {
        when(employees.findById("E404")).thenReturn(Optional.empty());

        VacationValidationException error = assertThrows(
                VacationValidationException.class,
                () -> service.create(request("E404", TODAY.plusDays(1), TODAY.plusDays(2))));

        assertEquals("empleado_inexistente", error.code);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsRetiredEmployee() {
        givenEmployee("E002", "RETIRADO");

        VacationValidationException error = assertThrows(
                VacationValidationException.class,
                () -> service.create(request("E002", TODAY.plusDays(1), TODAY.plusDays(2))));

        assertEquals("empleado_retirado", error.code);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void acceptsEmployeeWithPendingValidationStatus() {
        givenEmployee("E003", "PENDIENTE_VALIDACION");
        givenNoOverlaps("E003", TODAY.plusDays(1), TODAY.plusDays(2));
        givenNextNumber(1);

        assertEquals(VacationStatus.PROGRAMADA,
                service.create(request("E003", TODAY.plusDays(1), TODAY.plusDays(2))).estado());
    }

    // ---- solapamiento -------------------------------------------------------------------------

    @Test
    void rejectsOverlapAndReportsTheFirstConflictingVacation() {
        givenEmployee("E001", "ACTIVO");
        Vacation first = existing("V-2026-0001", "E001", TODAY.plusDays(5), TODAY.plusDays(9));
        Vacation second = existing("V-2026-0002", "E001", TODAY.plusDays(8), TODAY.plusDays(12));
        when(repository.findActiveOverlaps("E001", TODAY.plusDays(7), TODAY.plusDays(10)))
                .thenReturn(List.of(first, second));

        VacationConflictException error = assertThrows(
                VacationConflictException.class,
                () -> service.create(request("E001", TODAY.plusDays(7), TODAY.plusDays(10))));

        assertSame(first, error.conflict);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void queriesOverlapsWithTheExactInclusiveRangeRequested() {
        givenEmployee("E001", "ACTIVO");
        givenNoOverlaps("E001", TODAY.plusDays(20), TODAY.plusDays(30));
        givenNextNumber(1);

        service.create(request("E001", TODAY.plusDays(20), TODAY.plusDays(30)));

        verify(repository).findActiveOverlaps("E001", TODAY.plusDays(20), TODAY.plusDays(30));
    }

    @Test
    void doesNotPersistNorPublishWhenOverlapExists() {
        givenEmployee("E001", "ACTIVO");
        when(repository.findActiveOverlaps("E001", TODAY, TODAY))
                .thenReturn(List.of(existing("V-2026-0001", "E001", TODAY, TODAY)));

        assertThrows(VacationConflictException.class, () -> service.create(request("E001", TODAY, TODAY)));

        verify(repository, never()).nextNumber();
        verify(repository, never()).save(any());
    }

    // ---- utilidades ---------------------------------------------------------------------------

    private void givenEmployee(String id, String status) {
        when(employees.findById(id)).thenReturn(Optional.of(
                new EmployeeReplica(id, "Nombre", id.toLowerCase() + "@empresa.com", status, Instant.now(CLOCK))));
    }

    private void givenNoOverlaps(String employeeId, LocalDate start, LocalDate end) {
        when(repository.findActiveOverlaps(employeeId, start, end)).thenReturn(List.of());
    }

    private void givenNextNumber(long number) {
        when(repository.nextNumber()).thenReturn(number);
        when(repository.save(any(Vacation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static VacationRequest request(String employeeId, LocalDate start, LocalDate end) {
        return new VacationRequest(employeeId, start, end);
    }

    private static Vacation existing(String id, String employeeId, LocalDate start, LocalDate end) {
        return new Vacation(id, employeeId, start, end, Instant.now(CLOCK));
    }
}
