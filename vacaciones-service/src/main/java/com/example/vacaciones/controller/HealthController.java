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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@Tag(name = "Salud", description = "Comprobaciones de disponibilidad del servicio")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping({"", "/live"})
    @Operation(summary = "Comprobar que el proceso está vivo")
    @ApiResponse(responseCode = "200", description = "Proceso activo")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/ready")
    @Operation(summary = "Comprobar que el servicio está listo",
            description = "Ejecuta una consulta simple para verificar la conexión con PostgreSQL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Servicio listo"),
            @ApiResponse(responseCode = "500", description = "No fue posible comprobar la base de datos")
    })
    public Map<String, String> ready() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select 1")) {

            if (!resultSet.next()) {
                throw new IllegalStateException("database unavailable");
            }
        }

        return Map.of("status", "UP");
    }
}
