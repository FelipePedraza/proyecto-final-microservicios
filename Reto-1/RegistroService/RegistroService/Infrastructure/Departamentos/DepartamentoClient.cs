namespace RegistroService.Infrastructure.Departamentos;

public sealed class DepartamentoClient(HttpClient httpClient) : IDepartamentoClient
{
    private const int MaxRetries = 3;
    private static readonly TimeSpan RequestTimeout = TimeSpan.FromSeconds(5);

    public async Task<bool> ExisteAsync(
        string departamentoId,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(departamentoId);

        for (var attempt = 1; ; attempt++)
        {
            using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeoutCts.CancelAfter(RequestTimeout);

            try
            {
                using var response = await httpClient.GetAsync(
                    $"departamentos/{Uri.EscapeDataString(departamentoId)}",
                    HttpCompletionOption.ResponseHeadersRead,
                    timeoutCts.Token);

                if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
                {
                    return false;
                }

                if (IsTransientFailure(response.StatusCode))
                {
                    if (attempt >= MaxRetries)
                    {
                        response.EnsureSuccessStatusCode();
                    }

                    await Task.Delay(TimeSpan.FromSeconds(Math.Pow(2, attempt - 1)), cancellationToken);
                    continue;
                }

                response.EnsureSuccessStatusCode();
                return true;
            }
            catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested && attempt < MaxRetries)
            {
                await Task.Delay(TimeSpan.FromSeconds(Math.Pow(2, attempt - 1)), cancellationToken);
                continue;
            }
            catch (HttpRequestException) when (attempt < MaxRetries)
            {
                await Task.Delay(TimeSpan.FromSeconds(Math.Pow(2, attempt - 1)), cancellationToken);
                continue;
            }
        }
    }

    private static bool IsTransientFailure(System.Net.HttpStatusCode statusCode)
        => statusCode == System.Net.HttpStatusCode.RequestTimeout
            || statusCode == System.Net.HttpStatusCode.TooManyRequests
            || statusCode == System.Net.HttpStatusCode.BadGateway
            || statusCode == System.Net.HttpStatusCode.ServiceUnavailable
            || statusCode == System.Net.HttpStatusCode.GatewayTimeout
            || (int)statusCode >= 500;
}
