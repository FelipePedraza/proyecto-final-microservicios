package co.edu.uniquindio.auth_service.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.http.HttpStatus;

class HealthControllerTest {

    @Test
    void databaseDownReturns503WithoutRealDatabase() throws Exception {
        DataSource database = mock(DataSource.class);
        when(database.getConnection()).thenThrow(new java.sql.SQLException("connection refused"));

        var response = new HealthController(database, healthyRabbit()).health();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertChecks(response.getBody(), "DOWN", "UP");
    }

    @Test
    void brokerDownReturns503WithoutRealBroker() throws Exception {
        ConnectionFactory rabbit = mock(ConnectionFactory.class);
        when(rabbit.createConnection()).thenThrow(new AmqpConnectException(new RuntimeException("refused")));

        var response = new HealthController(healthyDatabase(), rabbit).health();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertChecks(response.getBody(), "UP", "DOWN");
    }

    private static DataSource healthyDatabase() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("select 1")).thenReturn(result);
        when(result.next()).thenReturn(true);
        return source;
    }

    private static ConnectionFactory healthyRabbit() {
        ConnectionFactory factory = mock(ConnectionFactory.class);
        org.springframework.amqp.rabbit.connection.Connection connection =
                mock(org.springframework.amqp.rabbit.connection.Connection.class);
        when(factory.createConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);
        return factory;
    }

    @SuppressWarnings("unchecked")
    private static void assertChecks(Map<String, Object> body, String database, String broker) {
        assertEquals("DOWN", body.get("status"));
        assertEquals("auth-service", body.get("service"));
        Map<String, String> checks = (Map<String, String>) body.get("checks");
        assertEquals(database, checks.get("database"));
        assertEquals(broker, checks.get("messageBroker"));
    }
}
