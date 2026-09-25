package com.example.vacaciones.messaging;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeEventConsumerTest {

    @Mock
    private ProcessedEventRepository events;

    @Mock
    private EmployeeReplicaRepository employees;

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
                new ObjectMapper(), events, employees, Clock.systemUTC());

        consumer.consume(payload);

        verify(employees).findById("E001");
        verify(employees, never()).findById(eventId);
    }

    @Test
    void ignoresVacationEventWithoutChangingEmployeeReplica() throws Exception {
        when(events.existsById("vacation-event")).thenReturn(false);
        EmployeeEventConsumer consumer = new EmployeeEventConsumer(
                new ObjectMapper(), events, employees, Clock.systemUTC());

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
    }

}