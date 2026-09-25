package com.example.vacaciones.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.vacaciones.domain.Vacation;

@Component
public class VacationEventPublisher {

    private final RabbitTemplate rabbit;

    public VacationEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(Vacation vacation) {
        if ("CANCELADA".equals(vacation.getStatus().name())) {
            return;
        }

        Map<String, Object> data = Map.of(
                "vacacionId", vacation.getId(),
                "empleadoId", vacation.getEmployeeId(),
                "fechaInicio", vacation.getStartDate().toString(),
                "fechaFin", vacation.getEndDate().toString()
        );

        Map<String, Object> envelope = Map.of(
                "id", UUID.randomUUID().toString(),
                "type", "vacaciones.programadas",
                "version", "1.0",
                "occurredAt", Instant.now().toString(),
                "producer", "vacaciones-service",
                "data", data
        );

        rabbit.convertAndSend("empleados_exchange", "", envelope);
    }
}
