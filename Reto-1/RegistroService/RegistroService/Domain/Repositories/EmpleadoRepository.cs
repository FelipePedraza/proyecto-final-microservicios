using RegistroService.Domain.Entities;
using RegistroService.Domain.Exceptions;

namespace RegistroService.Domain.Repositories;

/// <summary>
/// Implementación en memoria del repositorio de empleados.
/// Protege todas las operaciones con una sección crítica para garantizar
/// atómicamente la unicidad del id, email y número de empleado.
/// Esta implementación es válida para desarrollo y pruebas.
/// </summary>
public sealed class EmpleadoRepository : IEmpleadoRepository
{
    /// <summary>
    /// Almacenamiento en memoria de empleados. Key: ID del empleado, Value: Entidad Empleado.
    /// </summary>
    private readonly Dictionary<string, Empleado> _empleados = new(StringComparer.Ordinal);
    private readonly HashSet<string> _emails = new(StringComparer.OrdinalIgnoreCase);
    private readonly HashSet<string> _numerosEmpleado = new(StringComparer.Ordinal);
    private readonly object _sync = new();

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
        
        cancellationToken.ThrowIfCancellationRequested();

        lock (_sync)
        {
            var encontrado = _empleados.TryGetValue(id.Trim(), out var empleado);
            return Task.FromResult(encontrado ? empleado : null);
        }
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
        
        cancellationToken.ThrowIfCancellationRequested();

        lock (_sync)
        {
            return Task.FromResult(_emails.Contains(email.Trim()));
        }
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
        
        cancellationToken.ThrowIfCancellationRequested();

        lock (_sync)
        {
            return Task.FromResult(_numerosEmpleado.Contains(numeroEmpleado.Trim()));
        }
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
        cancellationToken.ThrowIfCancellationRequested();

        lock (_sync)
        {
            if (_empleados.ContainsKey(empleado.Id))
            {
                throw new EmpleadoDuplicadoException("id", empleado.Id);
            }

            if (_emails.Contains(empleado.Email))
            {
                throw new EmpleadoDuplicadoException("email", empleado.Email);
            }

            if (_numerosEmpleado.Contains(empleado.NumeroEmpleado))
            {
                throw new EmpleadoDuplicadoException(
                    "numeroEmpleado",
                    empleado.NumeroEmpleado);
            }

            _empleados.Add(empleado.Id, empleado);
            _emails.Add(empleado.Email);
            _numerosEmpleado.Add(empleado.NumeroEmpleado);
        }

        return Task.CompletedTask;
    }
}
