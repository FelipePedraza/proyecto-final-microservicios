using RegistroService.Domain.Entities;
using RegistroService.Domain.Exceptions;
using RegistroService.Domain.Repositories;

namespace RegistroService.Domain.Services;

/// <summary>
/// Coordina las operaciones de negocio disponibles para los empleados.
/// </summary>
public sealed class EmpleadoService
{
    private readonly IEmpleadoRepository _repository;

    public EmpleadoService(IEmpleadoRepository repository)
    {
        _repository = repository;
    }

    public async Task<Empleado> RegistrarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(empleado);

        if (await _repository.ExisteEmailAsync(empleado.Email, cancellationToken))
        {
            throw new EmpleadoDuplicadoException("email", empleado.Email);
        }

        if (await _repository.ExisteNumeroEmpleadoAsync(
                empleado.NumeroEmpleado,
                cancellationToken))
        {
            throw new EmpleadoDuplicadoException(
                "numeroEmpleado",
                empleado.NumeroEmpleado);
        }

        await _repository.RegistrarAsync(empleado, cancellationToken);
        return empleado;
    }

    public Task<Empleado?> BuscarPorIdAsync(
        string id,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(id);
        return _repository.ObtenerPorIdAsync(id.Trim(), cancellationToken);
    }
}
