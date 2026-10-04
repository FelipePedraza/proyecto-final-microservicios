package co.edu.uniquindio.auth_service.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JsonConverterAndConsumerTest {

    private final RabbitLifecycleConfig config = new RabbitLifecycleConfig();

    private Message json(String body) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    void elConversorBeanSerializaElEventEnvelopeDelIntegrante1ComoJson() {
        EventEnvelope<Object> envelope = new EventEnvelope<>();
        envelope.setId("evt-1");
        envelope.setType("cuenta.activada");
        envelope.setVersion(1);
        envelope.setOccurredAt(Instant.parse("2026-10-04T12:00:00Z"));
        envelope.setProducer("auth-service");
        envelope.setData(Map.of("empleadoId", "E001", "email", "a@b.co", "motivo", "ACTIVACION_INICIAL"));

        Message message = config.jsonMessageConverter().toMessage(envelope, new MessageProperties());

        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        assertEquals("application/json", message.getMessageProperties().getContentType());
        assertTrue(body.contains("\"type\":\"cuenta.activada\""), body);
        assertTrue(body.contains("\"occurredAt\":\"2026-10-04T12:00:00Z\""), body);
        assertTrue(body.contains("\"motivo\":\"ACTIVACION_INICIAL\""), body);
    }

    @Test
    void elConsumidorProcesaUnMensajeJsonConVersionIntOString() {
        AccountLifecycleService service = mock(AccountLifecycleService.class);
        AccountLifecycleConsumer consumer = new AccountLifecycleConsumer(service);

        consumer.consume(json("{\"id\":\"e1\",\"type\":\"vacaciones.iniciadas\",\"version\":1,\"data\":{\"empleadoId\":\"E001\"}}"));
        consumer.consume(json("{\"Id\":\"e2\",\"Type\":\"empleado.retirado\",\"Version\":\"1.0\",\"Data\":{\"Id\":\"E002\"}}"));

        verify(service).process("e1", "vacaciones.iniciadas", "E001", null);
        verify(service).process("e2", "empleado.retirado", "E002", null);
    }

    @Test
    void elConsumidorIgnoraEventosAjenosYRechazaEnvelopesInvalidos() {
        AccountLifecycleService service = mock(AccountLifecycleService.class);
        AccountLifecycleConsumer consumer = new AccountLifecycleConsumer(service);

        consumer.consume(json("{\"id\":\"e3\",\"type\":\"empleado.actualizado\",\"data\":{\"id\":\"E001\"}}"));
        verify(service, never()).process(any(), any(), any(), any());

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.consume(json("no es json")));
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.consume(json("{\"id\":\"e4\",\"type\":\"empleado.retirado\",\"data\":{}}")));
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.consume(json("{\"id\":\"e5\",\"type\":\"empleado.creado\",\"data\":{\"id\":\"E9\"}}")));
        verify(service, never()).process(eq("e5"), any(), any(), any());
    }
}
