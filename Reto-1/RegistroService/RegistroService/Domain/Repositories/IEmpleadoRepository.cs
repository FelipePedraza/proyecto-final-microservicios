using RegistroService.Domain.Entities;

namespace RegistroService.Domain.Repositories;

/// <summary>
/// Define las operaciones de persistencia que necesita el dominio de empleados.
/// </summary>
public interface IEmpleadoRepository
{
    Task<Empleado?> ObtenerPorIdAsync(
        string id,
        CancellationToken cancellationToken = default);

    Task RegistrarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default);

    /// <summary>

    /// Actualiza la información de un empleado existente (usado para PUT y para la baja lógica).

    /// </summary>

    Task ActualizarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default);

    /// <summary>

    /// Filtra empleados por estado RETIRADO y rango de fechas (Auditoría Reto 4).

    /// </summary>

    Task<IEnumerable<Empleado>> ObtenerRetiradosAsync(
        DateTime? desde,
        DateTime? hasta,
        CancellationToken cancellationToken = default);
}
