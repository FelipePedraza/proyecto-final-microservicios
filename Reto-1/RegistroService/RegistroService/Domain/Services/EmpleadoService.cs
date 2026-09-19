using Microsoft.Extensions.Logging.Abstractions;
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
    private readonly ILogger<EmpleadoService> _logger;

    public EmpleadoService(
        IEmpleadoRepository repository,
        IDepartamentoClient departamentoClient,
        ILogger<EmpleadoService>? logger = null)
    {
        _repository = repository;
        _departamentoClient = departamentoClient;
        _logger = logger ?? NullLogger<EmpleadoService>.Instance;
    }

    public async Task<Empleado> RegistrarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(empleado);

        await VerificarDepartamentoAsync(empleado, cancellationToken);

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

    /// <summary>
    /// Verifica el departamento. Si DepartamentosService no está disponible (llamada fallida tras
    /// reintentos o circuit breaker abierto) se aplica el FALLBACK: el empleado se registra igual
    /// con estado PENDIENTE_VALIDACION en lugar de rechazar el registro con un 503.
    /// Un departamento inexistente (404 de Departamentos) NO activa el fallback: es un error de negocio.
    /// </summary>
    private async Task VerificarDepartamentoAsync(Empleado empleado, CancellationToken cancellationToken)
    {
        try
        {
            if (!await _departamentoClient.ExisteAsync(empleado.DepartamentoId, cancellationToken))
            {
                throw new DepartamentoNoEncontradoException(empleado.DepartamentoId);
            }
        }
        catch (DepartamentosNoDisponibleException ex)
        {
            _logger.LogWarning(
                "Fallback: no se pudo verificar el departamento {DepartamentoId}; el empleado {EmpleadoId} " +
                "se registra como PENDIENTE_VALIDACION. Motivo: {Motivo}",
                empleado.DepartamentoId, empleado.Id, ex.Message);
            empleado.MarcarPendienteValidacion();
        }
    }
}
