package com.example.vacaciones.messaging;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class EmployeeEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEventConsumer.class);

    private final ObjectMapper mapper;
    private final ProcessedEventRepository events;
    private final EmployeeReplicaRepository employees;
    private final java.time.Clock clock;

    public EmployeeEventConsumer(
            ObjectMapper mapper,
            ProcessedEventRepository events,
            EmployeeReplicaRepository employees,
            java.time.Clock clock
    ) {
        this.mapper = mapper;
        this.events = events;
        this.employees = employees;
        this.clock = clock;
    }

    @RabbitListener(queues = "${RABBITMQ_QUEUE:vacaciones.empleados}", ackMode = "AUTO")
    @Transactional
    public void consume(String payload) throws Exception {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("event payload is required");
        }

        JsonNode root;
        try {
            root = mapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new AmqpRejectAndDontRequeueException("event payload must be valid JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("event payload must be a JSON object");
        }

        String eventId = getValue(root, "id");
        if (eventId == null) {
            eventId = getValue(root, "Id");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("event id is required");
        }
        if (events.existsById(eventId)) {
            return;
        }

        String type = getValue(root, "type");
        if (type == null) {
            type = getValue(root, "Type");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("event type is required");
        }
        if (!type.equals("empleado.creado")
                && !type.equals("empleado.actualizado")
                && !type.equals("empleado.retirado")) {
            log.debug("ignoring non-employee event eventId={} type={}", eventId, type);
            return;
        }

        JsonNode data = root.has("data")
            ? root.get("data")
            : root.has("Data") ? root.get("Data") : root;

        String employeeId = findEmployeeId(data);
        if (employeeId == null) {
            throw new IllegalArgumentException("employee id is required");
        }

        String status = "empleado.retirado".equals(type) ? "RETIRADO" : "ACTIVO";

        String name = getValue(data, "nombre");
        if (name == null) {
            name = getValue(data, "Nombre");
        }

        String email = getValue(data, "email");
        if (email == null) {
            email = getValue(data, "Email");
        }

        Instant now = Instant.now(clock);

        EmployeeReplica replica = employees.findById(employeeId)
                .orElse(new EmployeeReplica(employeeId, name, email, status, now));

        replica.update(name, email, status, now);
        employees.save(replica);
        events.save(new ProcessedEvent(eventId, now));

        log.info("employee event processed eventId={} employeeId={} type={}", eventId, employeeId, type);
    }

    private String findEmployeeId(JsonNode data) {
        String employeeId = getValue(data, "empleadoId");
        if (employeeId == null) {
            employeeId = getValue(data, "EmpleadoId");
        }
        if (employeeId == null) {
            employeeId = getValue(data, "id");
        }
        if (employeeId == null) {
            employeeId = getValue(data, "Id");
        }
        return employeeId;
    }

    private String getValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
