namespace RegistroService.API.DTOs;

/// <summary>
/// DTO (Data Transfer Object) para solicitud de creación de un nuevo empleado.
/// Contiene los datos necesarios para registrar un empleado en el sistema.
/// </summary>
public sealed class CreateEmpleadoRequest
{
    /// <summary>
    /// Identificador único del empleado.
    /// </summary>
    public string Id { get; set; } = null!;

    /// <summary>
    /// Nombre del empleado.
    /// Campo requerido y no puede estar vacío.
    /// </summary>
    public string Nombre { get; set; } = null!;

    /// <summary>
    /// Apellido del empleado.
    /// Campo requerido y no puede estar vacío.
    /// </summary>
    public string Apellido { get; set; } = null!;

    /// <summary>
    /// Email del empleado (debe ser único en el sistema).
    /// Se almacena en minúsculas automáticamente.
    /// Campo requerido y no puede estar vacío.
    /// </summary>
    public string Email { get; set; } = null!;

    /// <summary>
    /// Número identificador único del empleado (debe ser único en el sistema).
    /// Campo requerido y no puede estar vacío.
    /// </summary>
    public string NumeroEmpleado { get; set; } = null!;

    /// <summary>
    /// Cargo del empleado dentro de la organización.
    /// Campo requerido y no puede estar vacío.
    /// </summary>
    public string Cargo { get; set; } = null!;

    /// <summary>
    /// Área o departamento funcional del empleado.
    /// Campo requerido y no puede estar vacío.
    /// </summary>
    public string Area { get; set; } = null!;

    /// <summary>
    /// Identificador del departamento al que pertenece el empleado.
    /// Campo requerido y no puede estar vacío.
    /// </summary>
    public string DepartamentoId { get; set; } = null!;

    /// <summary>
    /// Fecha de ingreso del empleado a la organización.
    /// </summary>
    public DateOnly FechaIngreso { get; set; }
}
