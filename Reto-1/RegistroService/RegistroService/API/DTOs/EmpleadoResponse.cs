namespace RegistroService.API.DTOs;

/// <summary>
/// DTO (Data Transfer Object) para respuesta de empleado.
/// Contiene todos los datos de un empleado registrado en el sistema.
/// Se utiliza para retornar información del empleado en las respuestas de la API.
/// </summary>
public sealed class EmpleadoResponse
{
    /// <summary>
    /// Identificador único del empleado.
    /// </summary>
    public string Id { get; set; } = null!;

    /// <summary>
    /// Nombre del empleado.
    /// </summary>
    public string Nombre { get; set; } = null!;

    /// <summary>
    /// Apellido del empleado.
    /// </summary>
    public string Apellido { get; set; } = null!;

    /// <summary>
    /// Email del empleado (almacenado en minúsculas).
    /// </summary>
    public string Email { get; set; } = null!;

    /// <summary>
    /// Número identificador único del empleado.
    /// </summary>
    public string NumeroEmpleado { get; set; } = null!;

    /// <summary>
    /// Cargo del empleado dentro de la organización.
    /// </summary>
    public string Cargo { get; set; } = null!;

    /// <summary>
    /// Área o departamento funcional del empleado.
    /// </summary>
    public string Area { get; set; } = null!;

    /// <summary>
    /// Identificador del departamento al que pertenece el empleado.
    /// </summary>
    public string DepartamentoId { get; set; } = null!;

    /// <summary>
    /// Fecha de ingreso del empleado a la organización.
    /// </summary>
    public DateOnly FechaIngreso { get; set; }

    /// <summary>
    /// Estado canónico del empleado (ACTIVO, EN_VACACIONES, RETIRADO o PENDIENTE_VALIDACION).
    /// PENDIENTE_VALIDACION indica que el departamento no pudo verificarse al registrar
    /// (DepartamentosService no disponible o circuit breaker abierto).
    /// </summary>
    public string Estado { get; set; } = null!;
}
