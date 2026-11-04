package co.edu.uniquindio.auth_service.health;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final DataSource dataSource;
    private final ConnectionFactory rabbitConnectionFactory;

    public HealthController(DataSource dataSource, ConnectionFactory rabbitConnectionFactory) {
        this.dataSource = dataSource;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        String database = checkDatabase();
        String messageBroker = checkBroker();
        String status = "UP".equals(database) && "UP".equals(messageBroker) ? "UP" : "DOWN";
        return ResponseEntity.status("UP".equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", status,
                        "service", "auth-service",
                        "checks", Map.of("database", database, "messageBroker", messageBroker)
                ));
    }

    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select 1")) {
            return result.next() ? "UP" : "DOWN";
        } catch (Exception exception) {
            return "DOWN";
        }
    }

    private String checkBroker() {
        try {
            return rabbitConnectionFactory.createConnection().isOpen() ? "UP" : "DOWN";
        } catch (RuntimeException exception) {
            return "DOWN";
        }
    }
}
