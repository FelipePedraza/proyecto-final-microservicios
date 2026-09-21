namespace RegistroService.Domain.Exceptions;

/// <summary>
/// Excepción base para las reglas de negocio del dominio.
/// </summary>
public abstract class DomainException : Exception
{
    protected DomainException(string message) : base(message)
    {
    }
}
