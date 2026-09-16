namespace RegistroService.Infrastructure.Departamentos;

/// <summary>
/// La dependencia HTTP de departamentos no respondió. NO es un error de dominio:
/// el usuario no hizo nada mal, la infraestructura falló. Se traduce a 503.
/// </summary>
public sealed class DepartamentosNoDisponibleException : Exception
{
    public DepartamentosNoDisponibleException(string message, Exception? innerException = null)
        : base(message, innerException)
    {
    }
}