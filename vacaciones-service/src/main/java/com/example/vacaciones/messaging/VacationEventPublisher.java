package com.example.vacaciones.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import observability.RabbitTracePropagation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class VacationEventPublisher {

    private final RabbitTemplate rabbit;

    public VacationEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(VacationLifecycleEvent event) {
        Context parent = Context.current();
        Map<String, Object> data = Map.of(
                "vacacionId", event.vacationId(),
                "empleadoId", event.employeeId(),
                "fechaInicio", event.startDate().toString(),
                "fechaFin", event.endDate().toString()
        );

        Map<String, Object> envelope = Map.of(
                "id", UUID.randomUUID().toString(),
                "type", event.type(),
                "version", "1.0",
                "occurredAt", Instant.now().toString(),
                "producer", "vacaciones-service",
                "data", data
        );

        Span span = RabbitTracePropagation.startProducer(parent, "empleados_exchange", event.type());
        try (Scope ignored = span.makeCurrent()) {
            rabbit.convertAndSend("empleados_exchange", "", envelope, message -> {
                RabbitTracePropagation.inject(Context.current(), message.getMessageProperties());
                return message;
            });
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, exception.getMessage());
            throw exception;
        } finally {
            span.end();
        }
    }
}
