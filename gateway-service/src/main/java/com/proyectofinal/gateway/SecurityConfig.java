package com.proyectofinal.gateway;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Configuración base de seguridad reactiva.
 *
 * <p>La autenticación se realiza en {@link JwtAuthenticationFilter}. La cadena
 * evita que Spring Boot habilite Basic Auth o formularios y aplica RBAC por
 * método, ruta y propiedad del perfil.</p>
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class SecurityConfig {

    private static final String[] READ_PATHS = {
            "/empleados/**",
            "/departamentos/**",
            "/perfiles/**",
            "/notificaciones/**",
            "/vacaciones/**"
    };

    private static final byte[] UNAUTHORIZED_BODY = (
            "{\"error\":\"unauthorized\",\"message\":\"Autenticación requerida\"}")
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] FORBIDDEN_BODY = (
            "{\"error\":\"forbidden\",\"message\":\"No tiene permisos para realizar esta operación\"}")
            .getBytes(StandardCharsets.UTF_8);

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            GatewaySecurityProperties securityProperties,
            ProfileOwnershipAuthorizationManager profileOwnership) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, exception) ->
                                writeError(exchange, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_BODY))
                        .accessDeniedHandler((exchange, exception) ->
                                writeError(exchange, HttpStatus.FORBIDDEN, FORBIDDEN_BODY)))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(securityProperties.publicPaths().toArray(String[]::new)).permitAll()
                        .pathMatchers(HttpMethod.POST, "/auth/change-password").hasAnyRole("USER", "ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/perfiles/{empleadoId}").access(profileOwnership)
                        .pathMatchers(HttpMethod.GET, READ_PATHS).hasAnyRole("USER", "ADMIN")
                        .pathMatchers(HttpMethod.HEAD, READ_PATHS).hasAnyRole("USER", "ADMIN")
                        .anyExchange().hasRole("ADMIN"))
                .build();
    }

    private static Mono<Void> writeError(
            ServerWebExchange exchange,
            HttpStatus status,
            byte[] body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body)));
    }
}
