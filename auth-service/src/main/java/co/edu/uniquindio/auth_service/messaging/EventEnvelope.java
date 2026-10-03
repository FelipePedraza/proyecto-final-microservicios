package co.edu.uniquindio.auth_service.messaging;

import lombok.Data;
import java.time.Instant;

@Data
public class EventEnvelope<T> {
    private String id;
    private String type;
    private int version;
    private Instant occurredAt;
    private String producer;
    private T data;
}
