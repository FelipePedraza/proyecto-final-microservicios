package com.proyectofinal.gateway;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

/**
 * Valida en el Gateway los Access JWT emitidos por auth-service y propaga una
 * identidad confiable a los microservicios internos.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtAuthenticationFilter implements WebFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "USER");
    private static final byte[] UNAUTHORIZED_BODY = (
            "{\"error\":\"unauthorized\",\"message\":\"Token ausente o inválido\"}")
            .getBytes(StandardCharsets.UTF_8);

    private final SecretKey signingKey;
    private final Set<PathPattern> publicPathPatterns;

    public JwtAuthenticationFilter(
            @Value("${jwt.secret}") String secret,
            GatewaySecurityProperties securityProperties) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.secret debe tener al menos 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.publicPathPatterns = securityProperties.publicPaths().stream()
                .map(PathPatternParser.defaultInstance::parse)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isPublic(exchange)) {
            return chain.filter(removeUntrustedIdentityHeaders(exchange));
        }

        String token = extractBearerToken(exchange.getRequest().getHeaders());
        if (token == null) {
            return unauthorized(exchange);
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            String tokenType = claims.get("type", String.class);

            if (userId == null || userId.isBlank()
                    || role == null || !ALLOWED_ROLES.contains(role)
                    || claims.getExpiration() == null
                    || tokenType != null) {
                return unauthorized(exchange);
            }

            ServerHttpRequest authenticatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(USER_ID_HEADER);
                        headers.remove(USER_ROLE_HEADER);
                        headers.set(USER_ID_HEADER, userId);
                        headers.set(USER_ROLE_HEADER, role);
                    })
                    .build();

            var authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    token,
                    java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role)));

            return chain.filter(exchange.mutate().request(authenticatedRequest).build())
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
        } catch (JwtException | IllegalArgumentException exception) {
            return unauthorized(exchange);
        }
    }

    private boolean isPublic(ServerWebExchange exchange) {
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return true;
        }
        PathContainer path = exchange.getRequest().getPath().pathWithinApplication();
        return publicPathPatterns.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private String extractBearerToken(HttpHeaders headers) {
        List<String> authorizationHeaders = headers.get(HttpHeaders.AUTHORIZATION);
        if (authorizationHeaders == null || authorizationHeaders.size() != 1) {
            return null;
        }

        String authorization = authorizationHeaders.getFirst();
        if (authorization == null
                || authorization.length() <= BEARER_PREFIX.length()
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() || token.contains(" ") ? null : token;
    }

    private ServerWebExchange removeUntrustedIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(UNAUTHORIZED_BODY)));
    }
}
