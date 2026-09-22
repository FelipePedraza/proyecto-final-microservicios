using Microsoft.Extensions.Logging.Abstractions;
using RegistroService.Domain.Entities;
using RegistroService.Domain.Exceptions;
using RegistroService.Domain.Repositories;
using RegistroService.Infrastructure.Departamentos;

using RegistroService.Infrastructure.Messaging;

namespace RegistroService.Domain.Services;

/// <summary>
/// Coordina las operaciones de negocio disponibles para los empleados.
/// </summary>
public sealed class EmpleadoService
{
    private readonly IEmpleadoRepository _repository;
    private readonly IDepartamentoClient _departamentoClient;
    private readonly ILogger<EmpleadoService> _logger;
    private readonly IEventPublisher _eventPublisher;

    public EmpleadoService(
        IEmpleadoRepository repository,
        IDepartamentoClient departamentoClient,
        IEventPublisher eventPublisher,
        ILogger<EmpleadoService>? logger = null)
    {
        _repository = repository;
        _departamentoClient = departamentoClient;
        _eventPublisher = eventPublisher;
        _logger = logger ?? NullLogger<EmpleadoService>.Instance;
    }

    public async Task<Empleado> RegistrarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(empleado);

        await VerificarDepartamentoAsync(empleado, cancellationToken);

        await _repository.RegistrarAsync(empleado, cancellationToken);
        
        _eventPublisher.Publish("empleado.creado", empleado);
        
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
    /// Verifica el departamento. Si DepartamentosService no estÃ¡ disponible (llamada fallida tras
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

    // LÓGICA RETO 4: Busca el empleado, aplica baja lógica y dispara evento de retirado

    public async Task<Empleado?> RetirarAsync(string id, CancellationToken cancellationToken = default)
    {
        var empleado = await _repository.ObtenerPorIdAsync(id, cancellationToken);
        if (empleado == null)
        {
            return null; // El controlador manejarÃ¡ el 404
        }

        empleado.Retirar(DateTime.UtcNow);
        await _repository.ActualizarAsync(empleado, cancellationToken);

        _eventPublisher.Publish("empleado.retirado", empleado);

        return empleado;
    }

    // LÓGICA RETO 4: Modifica un empleado y dispara evento de actualizado

    public async Task<Empleado?> ActualizarAsync(string id, Empleado datosActualizados, CancellationToken cancellationToken = default)
    {
        var empleado = await _repository.ObtenerPorIdAsync(id, cancellationToken);
        if (empleado == null) return null;

        await _repository.ActualizarAsync(datosActualizados, cancellationToken);
        _eventPublisher.Publish("empleado.actualizado", datosActualizados);
        return datosActualizados;
    }

    // LÓGICA RETO 4: Expone la consulta de auditoría hacia el controlador

    public Task<IEnumerable<Empleado>> ObtenerRetiradosAsync(DateTime? desde, DateTime? hasta, CancellationToken cancellationToken = default)
    {
        return _repository.ObtenerRetiradosAsync(desde, hasta, cancellationToken);
    }
}
