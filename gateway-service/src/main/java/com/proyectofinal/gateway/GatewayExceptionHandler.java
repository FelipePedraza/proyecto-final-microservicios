package com.proyectofinal.gateway;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GatewayExceptionHandler {

        @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handle(
            ResponseStatusException ex,
            ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "error", ex.getStatusCode().value() == 404 ? "Recurso no encontrado" : "Error en el gateway",
                "path", exchange.getRequest().getPath().value())));
    }
}