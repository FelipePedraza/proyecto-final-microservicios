package com.example.vacaciones.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class VacationEventPublisherTest {

    @Test
    void publishesVacationLifecycleEnvelopeToEmployeesExchangeUsingStringVersion() throws Exception {
        RabbitTemplate rabbit = org.mockito.Mockito.mock(RabbitTemplate.class);
        VacationEventPublisher publisher = new VacationEventPublisher(rabbit);
        VacationLifecycleEvent event = new VacationLifecycleEvent(
                "vacaciones.finalizadas",
                "V-2026-0001",
                "E001",
                LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-05")
        );

        publisher.publish(event);

        ArgumentCaptor<Object> envelope = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq("empleados_exchange"), eq(""), envelope.capture());
        var value = new ObjectMapper().valueToTree(envelope.getValue());
        assertEquals("vacaciones.finalizadas", value.get("type").asText());
        org.junit.jupiter.api.Assertions.assertTrue(value.get("version").isTextual());
        assertEquals("1.0", value.get("version").asText());
        assertEquals("vacaciones-service", value.get("producer").asText());
        var data = value.get("data");
        assertEquals("V-2026-0001", data.get("vacacionId").asText());
        assertEquals("E001", data.get("empleadoId").asText());
        assertEquals("2026-10-01", data.get("fechaInicio").asText());
        assertEquals("2026-10-05", data.get("fechaFin").asText());
    }
}
