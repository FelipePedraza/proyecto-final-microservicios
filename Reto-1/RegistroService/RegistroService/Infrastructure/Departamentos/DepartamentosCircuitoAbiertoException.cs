namespace RegistroService.Infrastructure.Departamentos;

/// <summary>
/// El circuito hacia DepartamentosService está OPEN (o HALF_OPEN con una prueba en curso):
/// la llamada se rechazó de inmediato, sin tocar la red.
/// </summary>
public sealed class DepartamentosCircuitoAbiertoException : DepartamentosNoDisponibleException
{
    public DepartamentosCircuitoAbiertoException(string message, Exception? innerException = null)
        : base(message, innerException)
    {
    }
}
