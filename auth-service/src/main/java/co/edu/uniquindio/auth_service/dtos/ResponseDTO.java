package co.edu.uniquindio.auth_service.dtos;

public record ResponseDTO<T>(
    boolean error,
    T respuesta
) {}
