using System.Net;

namespace RegistroService.Infrastructure.Departamentos;

public sealed class DepartamentoClient(HttpClient httpClient, ILogger<DepartamentoClient> logger)
    : IDepartamentoClient
{
    private const int MaxRetries = 3;
    private static readonly TimeSpan RequestTimeout = TimeSpan.FromSeconds(5);

    public async Task<bool> ExisteAsync(string departamentoId, CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(departamentoId);

        Exception? ultimoError = null;

        for (var attempt = 1; attempt <= MaxRetries; attempt++)
        {
            using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeoutCts.CancelAfter(RequestTimeout);

            try
            {
                using var response = await httpClient.GetAsync(
                    $"departamentos/{Uri.EscapeDataString(departamentoId)}",
                    HttpCompletionOption.ResponseHeadersRead,
                    timeoutCts.Token);

                if (response.StatusCode == HttpStatusCode.NotFound)
                {
                    return false;   // caso de negocio: el departamento no existe
                }

                if (response.IsSuccessStatusCode)
                {
                    return true;
                }

                if (IsTransientFailure(response.StatusCode))
                {
                    ultimoError = new HttpRequestException(
                        $"Departamentos respondió {(int)response.StatusCode}.");
                    logger.LogWarning("Intento {Attempt}/{Max}: departamentos respondió {Status}.",
                        attempt, MaxRetries, (int)response.StatusCode);

                    if (attempt == MaxRetries) break;
                    await BackoffAsync(attempt, cancellationToken);
                    continue;
                }

                // 4xx no transitorio: el contrato entre servicios está roto, no es culpa del cliente.
                throw new DepartamentosNoDisponibleException(
                    $"El servicio de departamentos respondió {(int)response.StatusCode}, " +
                    "una respuesta no esperada para la verificación del departamento.");
            }
            catch (OperationCanceledException ex) when (!cancellationToken.IsCancellationRequested)
            {
                // Timeout propio de 5 s (no una cancelación del cliente HTTP entrante).
                ultimoError = ex;
                logger.LogWarning("Intento {Attempt}/{Max}: timeout consultando departamentos.", attempt, MaxRetries);
                if (attempt == MaxRetries) break;
                await BackoffAsync(attempt, cancellationToken);
            }
            catch (HttpRequestException ex)
            {
                // DNS, conexión rechazada, socket cerrado: el servicio no está arriba.
                ultimoError = ex;
                logger.LogWarning(ex, "Intento {Attempt}/{Max}: no se pudo contactar departamentos.", attempt, MaxRetries);
                if (attempt == MaxRetries) break;
                await BackoffAsync(attempt, cancellationToken);
            }
        }

        throw new DepartamentosNoDisponibleException(
            "El servicio de departamentos no está disponible en este momento. Intenta de nuevo más tarde.",
            ultimoError);
    }

    private static Task BackoffAsync(int attempt, CancellationToken cancellationToken)
        => Task.Delay(TimeSpan.FromSeconds(Math.Pow(2, attempt - 1)), cancellationToken);

    private static bool IsTransientFailure(HttpStatusCode statusCode)
        => statusCode == HttpStatusCode.RequestTimeout
            || statusCode == HttpStatusCode.TooManyRequests
            || (int)statusCode >= 500;
}