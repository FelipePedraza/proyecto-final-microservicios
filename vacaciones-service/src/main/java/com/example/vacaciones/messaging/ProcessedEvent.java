package com.example.vacaciones.messaging;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "eventos_procesados")
public class ProcessedEvent {

    @Id
    @Column(name = "id", length = 255)
    private String eventId;

    @Column(name = "procesado_en", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, Instant at) {
        this.eventId = eventId;
        this.processedAt = at;
    }
}
