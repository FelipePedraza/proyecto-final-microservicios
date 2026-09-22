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

    Task ActualizarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default);

    Task<IEnumerable<Empleado>> ObtenerRetiradosAsync(
        DateTime? desde,
        DateTime? hasta,
        CancellationToken cancellationToken = default);
}
