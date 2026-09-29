package com.example.vacaciones.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.vacaciones.service.VacationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeEventConsumerTest {

    @Mock
    private ProcessedEventRepository events;

    @Mock
    private EmployeeReplicaRepository employees;

    @Mock
    private VacationService vacations;

    @Test
    void consumesPascalCaseEnvelopeUsingEmployeeIdNotEventId() throws Exception {
        String eventId = "event-uuid";
        String payload = """
                {
                  "Id": "event-uuid",
                  "Type": "empleado.creado",
                  "Version": "1.0",
                  "Producer": "empleados-service",
                  "Data": {
                    "Id": "E001",
                    "Nombre": "Juan",
                    "Email": "juan.perez@empresa.com"
                  }
                }
                """;
        when(events.existsById(eventId)).thenReturn(false);
        when(employees.findById("E001")).thenReturn(Optional.empty());

        EmployeeEventConsumer consumer = new EmployeeEventConsumer(
                new ObjectMapper(), events, employees, vacations, Clock.systemUTC());

        consumer.consume(payload);

        verify(employees).findById("E001");
        verify(employees, never()).findById(eventId);
        verifyNoInteractions(vacations);
    }

    @Test
    void ignoresVacationEventWithoutChangingEmployeeReplica() throws Exception {
        when(events.existsById("vacation-event")).thenReturn(false);
        EmployeeEventConsumer consumer = new EmployeeEventConsumer(
                new ObjectMapper(), events, employees, vacations, Clock.systemUTC());

        consumer.consume("""
                {
                  "id": "vacation-event",
                  "type": "vacaciones.programadas",
                  "data": {
                    "vacacionId": "V-2026-0001",
                    "empleadoId": "E001",
                    "fechaInicio": "2026-10-01",
                    "fechaFin": "2026-10-10"
                  }
                }
                """);

        verifyNoInteractions(employees);
        verifyNoInteractions(vacations);
    }

    @Test
    void cancelsScheduledVacationsWhenEmployeeIsRetiredUsingPascalCasePayload() throws Exception {
        when(events.existsById("retirement-event")).thenReturn(false);
        when(employees.findById("E001")).thenReturn(Optional.empty());
        when(vacations.cancelScheduledByEmployee("E001")).thenReturn(2);
        EmployeeEventConsumer consumer = new EmployeeEventConsumer(
                new ObjectMapper(), events, employees, vacations, Clock.systemUTC());

        consumer.consume("""
                {
                  "Id": "retirement-event",
                  "Type": "empleado.retirado",
                  "Data": {
                    "Id": "E001",
                    "Nombre": "Juan",
                    "Email": "juan.perez@empresa.com"
                  }
                }
                """);

        ArgumentCaptor<EmployeeReplica> replicaCaptor = ArgumentCaptor.forClass(EmployeeReplica.class);
        InOrder processingOrder = Mockito.inOrder(employees, vacations, events);
        processingOrder.verify(employees).save(replicaCaptor.capture());
        assertEquals("RETIRADO", replicaCaptor.getValue().getStatus());
        processingOrder.verify(vacations).cancelScheduledByEmployee("E001");
        processingOrder.verify(events).save(any(ProcessedEvent.class));
    }

    @Test
    void doesNotCancelVacationsForCreatedOrUpdatedEmployees() throws Exception {
        when(events.existsById("created-event")).thenReturn(false);
        when(events.existsById("updated-event")).thenReturn(false);
        when(employees.findById("E001")).thenReturn(Optional.empty());
        EmployeeEventConsumer consumer = new EmployeeEventConsumer(
                new ObjectMapper(), events, employees, vacations, Clock.systemUTC());

        consumer.consume("""
                {"id":"created-event","type":"empleado.creado","data":{"id":"E001"}}
                """);
        consumer.consume("""
                {"id":"updated-event","type":"empleado.actualizado","data":{"id":"E001"}}
                """);

        verifyNoInteractions(vacations);
    }

    @Test
    void doesNotCancelVacationsAgainForDuplicateRetirementEvent() throws Exception {
        when(events.existsById("retirement-event")).thenReturn(true);
        EmployeeEventConsumer consumer = new EmployeeEventConsumer(
                new ObjectMapper(), events, employees, vacations, Clock.systemUTC());

        consumer.consume("""
                {"id":"retirement-event","type":"empleado.retirado","data":{"id":"E001"}}
                """);

        verifyNoInteractions(employees, vacations);
        verify(events, never()).save(any(ProcessedEvent.class));
    }

}