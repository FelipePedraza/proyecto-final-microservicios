package co.edu.uniquindio.auth_service.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publica en {@code auth_exchange} los eventos del ciclo de vida de cuentas, con el mismo envelope que
 * usan los demás servicios (version como string "1.0"). Si hay una transacción activa, el envío se hace
 * después del commit para no anunciar un cambio que luego se revierte.
 */
@Component
public class AuthEventPublisher {

    public static final String AUTH_EXCHANGE = "auth_exchange";
    private static final String PRODUCER = "auth-service";
    private static final String VERSION = "1.0";

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public AuthEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String type, Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("type", type);
        envelope.put("version", VERSION);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("producer", PRODUCER);
        envelope.put("data", data);

        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding("UTF-8");
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message message = new Message(mapper.writeValueAsBytes(envelope), props);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.send(AUTH_EXCHANGE, "", message);
                }
            });
        } else {
            rabbitTemplate.send(AUTH_EXCHANGE, "", message);
        }
    }
}
