using RegistroService.Domain.Entities;
using RegistroService.Domain.Exceptions;
using RegistroService.Domain.Repositories;
using RegistroService.Infrastructure.Departamentos;

namespace RegistroService.Domain.Services;

/// <summary>
/// Coordina las operaciones de negocio disponibles para los empleados.
/// </summary>
public sealed class EmpleadoService
{
    private readonly IEmpleadoRepository _repository;
    private readonly IDepartamentoClient _departamentoClient;

    public EmpleadoService(
        IEmpleadoRepository repository,
        IDepartamentoClient departamentoClient)
    {
        _repository = repository;
        _departamentoClient = departamentoClient;
    }

    public async Task<Empleado> RegistrarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(empleado);

        if (!await _departamentoClient.ExisteAsync(empleado.DepartamentoId, cancellationToken))
        {
            throw new DepartamentoNoEncontradoException(empleado.DepartamentoId);
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
