package com.example.vacaciones.controller;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@Tag(name = "Salud", description = "Comprobaciones de disponibilidad del servicio")
public class HealthController {

    private final DataSource dataSource;
    private final ConnectionFactory rabbitConnectionFactory;

    public HealthController(DataSource dataSource, ConnectionFactory rabbitConnectionFactory) {
        this.dataSource = dataSource;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
    }

    @GetMapping({"", "/live"})
    @Operation(summary = "Comprobar que el proceso está vivo")
    @ApiResponse(responseCode = "200", description = "Proceso activo")
    public ResponseEntity<Map<String, Object>> health() {
        return checkDependencies();
    }

    @GetMapping("/ready")
    @Operation(summary = "Comprobar que el servicio está listo",
            description = "Ejecuta una consulta simple para verificar la conexión con PostgreSQL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Servicio listo"),
            @ApiResponse(responseCode = "500", description = "No fue posible comprobar la base de datos")
    })
    public ResponseEntity<Map<String, Object>> ready() {
        return checkDependencies();
    }

    private ResponseEntity<Map<String, Object>> checkDependencies() {
        String database = "UP";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select 1")) {

            if (!resultSet.next()) {
                database = "DOWN";
            }
        } catch (Exception exception) {
            database = "DOWN";
        }

        String messageBroker;
        try {
            messageBroker = rabbitConnectionFactory.createConnection().isOpen() ? "UP" : "DOWN";
        } catch (RuntimeException exception) {
            messageBroker = "DOWN";
        }

        String status = "UP".equals(database) && "UP".equals(messageBroker) ? "UP" : "DOWN";
        Map<String, Object> body = Map.of(
                "status", status,
                "service", "vacaciones-service",
                "checks", Map.of("database", database, "messageBroker", messageBroker)
        );
        return ResponseEntity.status("UP".equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }
}
