package co.edu.uniquindio.auth_service.repository;

import co.edu.uniquindio.auth_service.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByEmail(String email);
}
