using RegistroService.Domain.Enums;

namespace RegistroService.Domain.Entities;

/// <summary>
/// Modelo canónico de un empleado del sistema.
/// </summary>
public sealed class Empleado
{
    public Empleado(
        string id,
        string nombre,
        string apellido,
        string email,
        string numeroEmpleado,
        string cargo,
        string area,
        string departamentoId,
        DateOnly fechaIngreso)
    {
        Id = Requerido(id, nameof(id));
        Nombre = Requerido(nombre, nameof(nombre));
        Apellido = Requerido(apellido, nameof(apellido));
        Email = Requerido(email, nameof(email)).ToLowerInvariant();
        NumeroEmpleado = Requerido(numeroEmpleado, nameof(numeroEmpleado));
        Cargo = Requerido(cargo, nameof(cargo));
        Area = Requerido(area, nameof(area));
        DepartamentoId = Requerido(departamentoId, nameof(departamentoId));
        if (fechaIngreso == default)
        {
            throw new ArgumentException("El valor es obligatorio.", nameof(fechaIngreso));
        }

        FechaIngreso = fechaIngreso;
        Estado = EstadoEmpleado.Activo;
    }

    public string Id { get; }

    public string Nombre { get; }

    public string Apellido { get; }

    public string Email { get; }

    public string NumeroEmpleado { get; }

    public string Cargo { get; }

    public string Area { get; }

    public string DepartamentoId { get; }

    public DateOnly FechaIngreso { get; }

    public EstadoEmpleado Estado { get; private set; }

    private static string Requerido(string valor, string nombreParametro)
    {
        if (string.IsNullOrWhiteSpace(valor))
        {
            throw new ArgumentException("El valor es obligatorio.", nombreParametro);
        }

        return valor.Trim();
    }
}
