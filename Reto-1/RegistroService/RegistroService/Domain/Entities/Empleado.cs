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

    private Empleado()
    {
    }

    public string Id { get; private set; } = null!;

    public string Nombre { get; private set; } = null!;

    public string Apellido { get; private set; } = null!;

    public string Email { get; private set; } = null!;

    public string NumeroEmpleado { get; private set; } = null!;

    public string Cargo { get; private set; } = null!;

    public string Area { get; private set; } = null!;

    public string DepartamentoId { get; private set; } = null!;

    public DateOnly FechaIngreso { get; private set; }

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
