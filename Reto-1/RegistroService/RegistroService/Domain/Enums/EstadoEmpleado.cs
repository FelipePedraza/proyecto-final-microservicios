namespace RegistroService.Domain.Enums;

/// <summary>
/// Estados posibles de un empleado durante su ciclo de vida.
/// Un empleado se registra como activo; si no se pudo verificar su departamento
/// (circuit breaker abierto o DepartamentosService caído) queda pendiente de validación.
/// </summary>
public enum EstadoEmpleado
{
    Activo,
    EnVacaciones,
    Retirado,

    /// <summary>Fallback del Reto 3: el departamento no pudo verificarse al momento del registro.</summary>
    PendienteValidacion
}
