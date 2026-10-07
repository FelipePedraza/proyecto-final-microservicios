namespace EmpleadoService.Domain.Exceptions;

public sealed class EmpleadoYaRetiradoException : DomainException
{
    public EmpleadoYaRetiradoException(string empleadoId)
        : base($"El empleado con id {empleadoId} ya está retirado.")
    {
    }
}
