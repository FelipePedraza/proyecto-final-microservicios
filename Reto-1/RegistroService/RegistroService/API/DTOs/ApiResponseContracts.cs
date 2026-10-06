using RegistroService.Domain.Enums;

namespace RegistroService.API.DTOs;

/// <summary>Standard error response returned by RegistroService.</summary>
public sealed record ErrorResponse(string Error);

/// <summary>Response returned by the liveness endpoint.</summary>
public sealed record HealthResponse(string Status);

/// <summary>Response returned by the readiness endpoint when it can report database state.</summary>
public sealed record ReadinessResponse(string Status, string Database);

/// <summary>Current dependency and circuit-breaker state.</summary>
public sealed record CircuitBreakerHealthResponse(
    string Dependencia,
    string Estado,
    int FallosConsecutivos,
    double DuracionCircuitoAbiertoSegundos);

/// <summary>Employee fields returned by the retired-employees audit query.</summary>
public sealed record RetiredEmpleadoResponse(
    string Id,
    string Nombre,
    string Apellido,
    string Email,
    string NumeroEmpleado,
    string Cargo,
    string Area,
    string DepartamentoId,
    DateOnly FechaIngreso,
    EstadoEmpleado Estado,
    DateTime? FechaRetiro);
