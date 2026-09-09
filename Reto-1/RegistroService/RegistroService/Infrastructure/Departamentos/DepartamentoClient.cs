namespace RegistroService.Infrastructure.Departamentos;

public sealed class DepartamentoClient(HttpClient httpClient) : IDepartamentoClient
{
    public async Task<bool> ExisteAsync(
        string departamentoId,
        CancellationToken cancellationToken = default)
    {
        using var response = await httpClient.GetAsync(
            $"departamentos/{Uri.EscapeDataString(departamentoId)}",
            cancellationToken);

        if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            return false;
        }

        response.EnsureSuccessStatusCode();
        return true;
    }
}
