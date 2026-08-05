namespace RegistroService.Domain.Exceptions;

/// <summary>
/// Se produce al intentar registrar un email o número de empleado existente.
/// </summary>
public sealed class EmpleadoDuplicadoException : DomainException
{
    public EmpleadoDuplicadoException(string campo, string valor)
        : base($"Ya existe un empleado con {campo} '{valor}'.")
    {
        Campo = campo;
        Valor = valor;
    }

    public string Campo { get; }

    public string Valor { get; }
}
