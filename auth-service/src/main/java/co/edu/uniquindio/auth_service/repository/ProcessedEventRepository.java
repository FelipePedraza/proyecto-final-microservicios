package co.edu.uniquindio.auth_service.repository;

import co.edu.uniquindio.auth_service.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
