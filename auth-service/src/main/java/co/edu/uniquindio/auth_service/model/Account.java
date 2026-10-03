package co.edu.uniquindio.auth_service.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id; // Será el mismo ID del empleado

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = true) // Puede ser nula al principio antes de activarla
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;
}
