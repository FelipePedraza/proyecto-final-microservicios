namespace RegistroService.Infrastructure.Departamentos;

public interface IDepartamentoClient
{
    Task<bool> ExisteAsync(string departamentoId, CancellationToken cancellationToken = default);
}
