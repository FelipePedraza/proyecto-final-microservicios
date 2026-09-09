namespace RegistroService.Domain.Exceptions;

public sealed class DepartamentoNoEncontradoException : DomainException
{
    public DepartamentoNoEncontradoException(string departamentoId)
        : base($"El departamento con id {departamentoId} no existe.")
    {
    }
}
