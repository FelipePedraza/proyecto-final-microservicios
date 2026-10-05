package co.edu.uniquindio.auth_service.config;

import co.edu.uniquindio.auth_service.model.Account;
import co.edu.uniquindio.auth_service.model.AccountStatus;
import co.edu.uniquindio.auth_service.model.Role;
import co.edu.uniquindio.auth_service.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.default.email:admin@empresa.com}")
    private String adminEmail;

    @Value("${admin.default.password}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        // Revisamos si ya existe alguien con este correo
        Optional<Account> adminOpt = accountRepository.findByEmail(adminEmail);
        
        if (adminOpt.isEmpty()) {
            log.info("No se encontró cuenta de Administrador. Creando admin por defecto...");
            
            Account adminAccount = Account.builder()
                    .id("ADMIN-001") // ID representativo para el admin inicial
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .role(Role.ADMIN)
                    .status(AccountStatus.ACTIVA)
                    .build();
                    
            accountRepository.save(adminAccount);
            log.info("¡Administrador inicial creado con éxito! Email: {}", adminEmail);
            log.warn("NOTA: Por favor cambia esta contraseña en producción usando el endpoint /auth/change-password");
        } else {
            log.info("La cuenta de Administrador ya existe en la base de datos.");
        }
    }
}
