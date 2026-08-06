using System.Collections.Concurrent;
using System.Linq;
using RegistroService.Domain.Entities;

namespace RegistroService.Domain.Repositories;

/// <summary>
/// Implementación en memoria del repositorio de empleados.
/// Utiliza ConcurrentDictionary para acceso thread-safe.
/// Esta implementación es válida para desarrollo y pruebas.
/// </summary>
public sealed class EmpleadoRepository : IEmpleadoRepository
{
    /// <summary>
    /// Almacenamiento en memoria de empleados. Key: ID del empleado, Value: Entidad Empleado.
    /// </summary>
    private readonly ConcurrentDictionary<string, Empleado> _empleados = new();

    /// <summary>
    /// Obtiene un empleado por su identificador único.
    /// </summary>
    /// <param name="id">Identificador único del empleado.</param>
    /// <param name="cancellationToken">Token de cancelación para operaciones asincrónicas.</param>
    /// <returns>
    /// Una tarea que retorna el empleado si existe, null si no se encuentra.
    /// </returns>
    public Task<Empleado?> ObtenerPorIdAsync(
        string id,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(id);
        
        var encontrado = _empleados.TryGetValue(id.Trim(), out var empleado);
        return Task.FromResult(encontrado ? empleado : null);
    }

    /// <summary>
    /// Verifica si existe un empleado con el email especificado.
    /// La búsqueda es case-insensitive porque los emails se almacenan en minúsculas.
    /// </summary>
    /// <param name="email">Email a buscar (será convertido a minúsculas).</param>
    /// <param name="cancellationToken">Token de cancelación para operaciones asincrónicas.</param>
    /// <returns>True si el email existe, false en caso contrario.</returns>
    public Task<bool> ExisteEmailAsync(
        string email,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(email);
        
        var emailNormalizado = email.Trim().ToLowerInvariant();
        var existe = _empleados.Values.Any(e => e.Email == emailNormalizado);
        
        return Task.FromResult(existe);
    }

    /// <summary>
    /// Verifica si existe un empleado con el número de empleado especificado.
    /// </summary>
    /// <param name="numeroEmpleado">Número de empleado a buscar.</param>
    /// <param name="cancellationToken">Token de cancelación para operaciones asincrónicas.</param>
    /// <returns>True si el número de empleado existe, false en caso contrario.</returns>
    public Task<bool> ExisteNumeroEmpleadoAsync(
        string numeroEmpleado,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(numeroEmpleado);
        
        var numeroNormalizado = numeroEmpleado.Trim();
        var existe = _empleados.Values.Any(e => e.NumeroEmpleado == numeroNormalizado);
        
        return Task.FromResult(existe);
    }

    /// <summary>
    /// Registra un nuevo empleado en el repositorio.
    /// </summary>
    /// <param name="empleado">Empleado a registrar. No puede ser null.</param>
    /// <param name="cancellationToken">Token de cancelación para operaciones asincrónicas.</param>
    /// <returns>Una tarea que representa la operación asincrónica.</returns>
    /// <exception cref="ArgumentNullException">Se lanza si empleado es null.</exception>
    public Task RegistrarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(empleado);
        
        _empleados.AddOrUpdate(empleado.Id, empleado, (_, __) => empleado);
        return Task.CompletedTask;
    }
}
