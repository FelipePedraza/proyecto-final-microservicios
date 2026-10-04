package co.edu.uniquindio.auth_service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Consume el fanout {@code empleados_exchange} y delega el ciclo de vida de cuentas en
 * {@link AccountLifecycleService}. Un envelope mal formado se rechaza sin reencolar (va a la DLQ);
 * cualquier otro error se propaga para que el mensaje se reintente.
 */
@Component
public class AccountLifecycleConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountLifecycleConsumer.class);

    private final AccountLifecycleService lifecycle;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public AccountLifecycleConsumer(AccountLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @RabbitListener(queues = RabbitLifecycleConfig.AUTH_QUEUE)
    public void consume(Message message) {
        JsonNode root;
        try {
            root = mapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new AmqpRejectAndDontRequeueException("El mensaje no es JSON válido", e);
        }
        if (root == null || !root.isObject()) {
            throw new AmqpRejectAndDontRequeueException("El mensaje no es un objeto JSON");
        }

        String type = text(root, "type", "Type");
        if (!AccountLifecycleService.isLifecycleEvent(type)) {
            log.debug("Evento {} ignorado por auth-service.", type);
            return;
        }

        String eventId = text(root, "id", "Id");
        JsonNode data = root.has("data") ? root.get("data") : root.get("Data");
        String empleadoId = data == null ? null : text(data, "empleadoId", "EmpleadoId", "id", "Id");
        String email = data == null ? null : text(data, "email", "Email");

        if (eventId == null || empleadoId == null) {
            throw new AmqpRejectAndDontRequeueException(
                    "Envelope incompleto para " + type + " (id=" + eventId + ", empleadoId=" + empleadoId + ")");
        }
        if (AccountLifecycleService.EMPLEADO_CREADO.equals(type) && email == null) {
            throw new AmqpRejectAndDontRequeueException("empleado.creado sin email (evento " + eventId + ")");
        }

        lifecycle.process(eventId, type, empleadoId, email);
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && !value.asString().isBlank()) {
                return value.asString();
            }
        }
        return null;
    }
}
