package com.example.vacaciones.messaging;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "empleados_validos")
public class EmployeeReplica {

    @Id
    @Column(name = "id", length = 100)
    private String employeeId;

    private String nombre;
    private String email;

    @Column(name = "estado", nullable = false)
    private String status;

    @Column(name = "actualizado_en", nullable = false)
    private Instant updatedAt;

    protected EmployeeReplica() {
    }

    public EmployeeReplica(String id, String name, String mail, String status, Instant at) {
        this.employeeId = id;
        this.nombre = name;
        this.email = mail;
        this.status = status;
        this.updatedAt = at;
    }

    public void update(String name, String mail, String status, Instant at) {
        this.nombre = name;
        this.email = mail;
        this.status = status;
        this.updatedAt = at;
    }

    public String getStatus() {
        return status;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }
    
}
