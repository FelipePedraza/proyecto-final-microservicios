package com.proyectofinal.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "clave-compartida-de-pruebas-con-mas-de-32-bytes";

    private JwtAuthenticationFilter filter;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                SECRET,
                new GatewaySecurityProperties(List.of(
                        "/health",
                        "/fallback/**",
                        "/auth/login",
                        "/auth/recover-password",
                        "/auth/reset-password")));
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void permiteRutaPublicaSinTokenYEliminaCabecerasNoConfiables() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login")
                        .header(JwtAuthenticationFilter.USER_ID_HEADER, "suplantado")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return current.getResponse().setComplete();
        }).block();

        assertNull(forwarded.get().getRequest().getHeaders()
                .getFirst(JwtAuthenticationFilter.USER_ID_HEADER));
    }

    @Test
    void rechazaRutaProtegidaSinBearer() {
        MockServerWebExchange exchange = exchangeFor("/perfiles/E001", null);

        filter.filter(exchange, current -> current.getResponse().setComplete()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void rechazaTokenMalformado() {
        assertUnauthorized("esto-no-es-un-jwt");
    }

    @Test
    void propagaIdentidadDelAccessTokenYReemplazaCabecerasDelCliente() {
        String token = token("E001", Map.of("role", "USER"), 60_000);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/perfiles/E001")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(JwtAuthenticationFilter.USER_ID_HEADER, "suplantado")
                        .header(JwtAuthenticationFilter.USER_ROLE_HEADER, "ADMIN")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return ReactiveSecurityContextHolder.getContext()
                    .doOnNext(context -> authentication.set(context.getAuthentication()))
                    .then();
        }).block();

        assertEquals("E001", forwarded.get().getRequest().getHeaders()
                .getFirst(JwtAuthenticationFilter.USER_ID_HEADER));
        assertEquals("USER", forwarded.get().getRequest().getHeaders()
                .getFirst(JwtAuthenticationFilter.USER_ROLE_HEADER));
        assertEquals("E001", authentication.get().getPrincipal());
        assertEquals("ROLE_USER", authentication.get().getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void rechazaTokenExpirado() {
        assertUnauthorized(token("E001", Map.of("role", "USER"), -1_000));
    }

    @Test
    void rechazaTokenSinClaimDeExpiracion() {
        String tokenWithoutExpiration = Jwts.builder()
                .subject("E001")
                .claim("role", "USER")
                .issuedAt(new Date())
                .signWith(signingKey)
                .compact();

        assertUnauthorized(tokenWithoutExpiration);
    }

    @Test
    void rechazaTokenConFirmaIncorrecta() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "otra-clave-de-pruebas-con-mas-de-treinta-y-dos-bytes".getBytes(StandardCharsets.UTF_8));
        String wrongSignature = Jwts.builder()
                .subject("E001")
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();
        assertUnauthorized(wrongSignature);
    }

    @Test
    void rechazaTokenDeRecuperacionEnRutaProtegida() {
        assertUnauthorized(token(
                "E001",
                Map.of("role", "USER", "type", "RESET_PASSWORD"),
                60_000));
    }

    @Test
    void rechazaAccessTokenSinSubjectORolValido() {
        assertUnauthorized(token(null, Map.of("role", "USER"), 60_000));
        assertUnauthorized(token("E001", Map.of(), 60_000));
        assertUnauthorized(token("E001", Map.of("role", "SUPERADMIN"), 60_000));
    }

    private void assertUnauthorized(String token) {
        MockServerWebExchange exchange = exchangeFor("/empleados/E001", token);
        filter.filter(exchange, current -> current.getResponse().setComplete()).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    private MockServerWebExchange exchangeFor(String path, String token) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return MockServerWebExchange.from(request.build());
    }

    private String token(String subject, Map<String, Object> claims, long validityMillis) {
        var builder = Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + validityMillis));
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.signWith(signingKey).compact();
    }
}
