using EmpleadoService.Domain.Entities;

namespace EmpleadoService.Domain.Repositories;

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

    /// <summary>Actualiza un empleado existente, incluso para realizar su baja lógica.</summary>
    Task ActualizarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default);

    /// <summary>Filtra empleados retirados por un rango opcional de fechas de retiro.</summary>
    Task<IEnumerable<Empleado>> ObtenerRetiradosAsync(
        DateTime? desde,
        DateTime? hasta,
        CancellationToken cancellationToken = default);
}
