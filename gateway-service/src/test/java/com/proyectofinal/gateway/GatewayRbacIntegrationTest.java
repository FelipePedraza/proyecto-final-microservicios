package com.proyectofinal.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jwt.secret=" + GatewayRbacIntegrationTest.SECRET,
        "GATEWAY_CONNECT_TIMEOUT_MS=3000",
        "GATEWAY_RESPONSE_TIMEOUT=15s",
        "AUTH_SERVICE_URL=http://localhost:8089",
        "EMPLEADOS_SERVICE_URL=http://localhost:8080",
        "DEPARTAMENTO_SERVICE_URL=http://localhost:8081",
        "PERFILES_SERVICE_URL=http://localhost:8083",
        "NOTIFICACIONES_SERVICE_URL=http://localhost:8082",
        "VACACIONES_SERVICE_URL=http://localhost:8085"
})
class GatewayRbacIntegrationTest {

    static final String SECRET = "clave-compartida-de-pruebas-con-mas-de-32-bytes";

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void healthEsPublico() {
        client.get()
                .uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.service").isEqualTo("gateway-service")
                .jsonPath("$.checks").isMap();
    }

    @Test
    void prometheusEsPublicoYExponeMetricasDelProceso() {
        client.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("jvm_memory_used_bytes")));
    }

    @Test
    void openApiEsPublicoYDeclaraBearerAuth() {
        client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.securitySchemes.BearerAuth.type").isEqualTo("http")
                .jsonPath("$.components.securitySchemes.BearerAuth.scheme").isEqualTo("bearer")
                .jsonPath("$.components.securitySchemes.BearerAuth.bearerFormat").isEqualTo("JWT")
                .jsonPath("$.security[0].BearerAuth").isArray();
    }

    @Test
    void swaggerUiAgregaLasEspecificacionesDeLosServicios() {
        client.get()
                .uri("/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(config -> {
                    assertTrue(config.contains("/openapi/auth"));
                    assertTrue(config.contains("/openapi/registro"));
                    assertTrue(config.contains("/openapi/departamentos"));
                    assertTrue(config.contains("/openapi/perfiles"));
                    assertTrue(config.contains("/openapi/notificaciones"));
                    assertTrue(config.contains("/openapi/vacaciones"));
                });
    }

    @Test
    void rolUserEsInsuficienteParaCrearDepartamentos() {
        client.post()
                .uri("/departamentos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("E001", "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error").isEqualTo("forbidden");
    }

    @Test
    void userNoPuedeModificarPerfilAjeno() {
        client.put()
                .uri("/perfiles/E999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("E001", "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error").isEqualTo("forbidden");
    }

    private String token(String subject, String role) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
