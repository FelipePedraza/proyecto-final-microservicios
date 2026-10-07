package com.example.vacaciones.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "vacaciones",
    indexes = @Index(name = "ix_vacaciones_empleado_fechas", columnList = "empleado_id,fecha_inicio,fecha_fin")
)
public class Vacation {

    @Id
    @Column(length = 20)
    private String id;

    @Column(name = "empleado_id", nullable = false, length = 100)
    private String employeeId;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate startDate;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private VacationStatus status = VacationStatus.PROGRAMADA;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant createdAt;

    protected Vacation() {
    }

    public Vacation(String id, String employeeId, LocalDate startDate, LocalDate endDate, Instant createdAt) {
        this.id = id;
        this.employeeId = employeeId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
    }

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = VacationStatus.PROGRAMADA;
        }
    }

    public String getId() {
        return id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public VacationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Días de vacaciones del período; ambos extremos son inclusivos, por lo que un período de un solo día vale 1. */
    public long totalDays() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public void cancel() {
        if (status != VacationStatus.PROGRAMADA) {
            throw new IllegalStateException("Solo se puede cancelar un período PROGRAMADA");
        }
        status = VacationStatus.CANCELADA;
    }

    public void start() {
        if (status != VacationStatus.PROGRAMADA) {
            throw new IllegalStateException("Solo se puede iniciar un período PROGRAMADA");
        }
        status = VacationStatus.EN_CURSO;
    }

    public void finish() {
        if (status != VacationStatus.EN_CURSO) {
            throw new IllegalStateException("Solo se puede finalizar un período EN_CURSO");
        }
        status = VacationStatus.FINALIZADA;
    }
}
