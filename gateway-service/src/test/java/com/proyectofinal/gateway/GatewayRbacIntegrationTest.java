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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jwt.secret=" + GatewayRbacIntegrationTest.SECRET
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
                .expectStatus().isOk();
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
