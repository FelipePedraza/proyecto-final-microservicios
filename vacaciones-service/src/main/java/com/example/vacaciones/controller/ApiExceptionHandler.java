package com.example.vacaciones.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.vacaciones.dto.VacationResponse;
import com.example.vacaciones.exception.VacationConflictException;
import com.example.vacaciones.exception.VacationNotFoundException;
import com.example.vacaciones.exception.VacationStateException;
import com.example.vacaciones.exception.VacationValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(VacationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception exception) {
        return buildResponse(HttpStatus.NOT_FOUND, "no_encontrada", exception.getMessage(), null);
    }

    @ExceptionHandler(VacationStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(Exception exception) {
        return buildResponse(HttpStatus.CONFLICT, "estado_invalido", exception.getMessage(), null);
    }

    @ExceptionHandler(VacationConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(VacationConflictException exception) {
        Object conflict = (exception.conflict == null) ? null : VacationResponse.from(exception.conflict);
        return buildResponse(HttpStatus.BAD_REQUEST, "solapamiento", exception.getMessage(), conflict);
    }

    @ExceptionHandler(VacationValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(VacationValidationException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.code, exception.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
            .map(error -> error.getDefaultMessage())
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining("; "));

        if (message.isBlank()) {
            message = "El cuerpo contiene campos ausentes o inválidos";
        }

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "validacion",
            message,
            null
        );
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            "validacion",
            "El cuerpo JSON es inválido o contiene valores con formato incorrecto (fechas: yyyy-MM-dd)",
                null
        );
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status,
            String code,
            String message,
            Object conflict
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", code);
        response.put("message", message);

        if (conflict != null) {
            response.put("conflicto", conflict);
        }

        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(response);
    }
}
