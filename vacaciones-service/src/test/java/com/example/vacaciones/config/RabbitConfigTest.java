package com.example.vacaciones.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

class RabbitConfigTest {

    @Test
    void outgoingMessagesUseJsonConverter() throws Exception {
        RabbitTemplate template = new RabbitConfig().rabbitTemplate(mock(ConnectionFactory.class));
        Map<String, Object> envelope = Map.of(
                "id", "event-uuid",
                "type", "vacaciones.programadas",
                "data", Map.of("empleadoId", "E001", "fechaInicio", "2026-10-01")
        );

        assertInstanceOf(JacksonJsonMessageConverter.class, template.getMessageConverter());
        Message message = template.getMessageConverter().toMessage(envelope, new MessageProperties());

        assertEquals("application/json", message.getMessageProperties().getContentType());
        var decoded = new ObjectMapper().readTree(message.getBody());
        assertEquals("vacaciones.programadas", decoded.get("type").asText());
        assertEquals("E001", decoded.get("data").get("empleadoId").asText());
    }

    @Test
    void incomingMessagesAreDecodedAsUtf8RegardlessOfContentType() {
        String json = "{\"id\":\"event-uuid\",\"type\":\"empleado.creado\"}";
        Message message = new Message(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), new MessageProperties());
        message.getMessageProperties().setContentType("application/x-java-serialized-object");

        MessageConverter converter = new RabbitConfig().employeeEventMessageConverter();

        assertEquals(json, converter.fromMessage(message));
    }
}