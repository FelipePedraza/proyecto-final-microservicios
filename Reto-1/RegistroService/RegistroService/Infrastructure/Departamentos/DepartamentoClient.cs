using System.Net;
using Microsoft.Extensions.Options;
using Polly.CircuitBreaker;

namespace RegistroService.Infrastructure.Departamentos;

/// <summary>
/// Cliente HTTP de DepartamentosService. Capas de resiliencia, de afuera hacia adentro:
///   1. Reintentos con backoff exponencial (hasta <see cref="MaxRetries"/> intentos).
///   2. Circuit breaker: CADA intento fallido cuenta como un fallo consecutivo, de modo que el
///      circuito se abre aunque la petición del usuario se corte antes de agotar los reintentos.
///      Si el circuito se abre, se deja de reintentar de inmediato.
///   3. Timeout por intento (<see cref="DepartamentosResilienceOptions.TimeoutLlamada"/>).
/// </summary>
public sealed class DepartamentoClient(
    HttpClient httpClient,
    ILogger<DepartamentoClient> logger,
    DepartamentoCircuitBreaker circuitBreaker,
    IOptions<DepartamentosResilienceOptions> options)
    : IDepartamentoClient
{
    private const int MaxRetries = 3;
    private readonly DepartamentosResilienceOptions _options = options.Value;

    /// <summary>
    /// Verifica si el departamento existe. Si el circuito está abierto lanza
    /// <see cref="DepartamentosCircuitoAbiertoException"/> sin contactar a Departamentos.
    /// </summary>
    public async Task<bool> ExisteAsync(string departamentoId, CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(departamentoId);

        Exception? ultimoError = null;

        for (var attempt = 1; attempt <= MaxRetries; attempt++)
        {
            try
            {
                return await circuitBreaker.EjecutarAsync(
                    token => IntentarAsync(departamentoId, token),
                    cancellationToken);
            }
            catch (BrokenCircuitException ex)
            {
                // Circuito OPEN (o HALF_OPEN con otra llamada de prueba en curso): no se reintenta.
                logger.LogWarning(
                    "Circuito de departamentos {Estado}: llamada rechazada sin contactar al servicio.",
                    circuitBreaker.Estado);
                throw new DepartamentosCircuitoAbiertoException(
                    "El circuito hacia el servicio de departamentos está abierto; " +
                    "no se intentó la llamada. Intenta de nuevo más tarde.",
                    ex);
            }
            catch (FalloNoReintentableException)
            {
                // 4xx no transitorio: el contrato entre servicios está roto, reintentar no ayuda.
                throw;
            }
            catch (DepartamentosNoDisponibleException ex)
            {
                ultimoError = ex;
                logger.LogWarning(ex.InnerException, "Intento {Attempt}/{Max} fallido: {Motivo}",
                    attempt, MaxRetries, ex.Message);

                if (attempt == MaxRetries) break;
                await BackoffAsync(attempt, cancellationToken);
            }
        }

        throw new DepartamentosNoDisponibleException(
            "El servicio de departamentos no está disponible en este momento. Intenta de nuevo más tarde.",
            ultimoError);
    }

    /// <summary>
    /// Un único intento HTTP con su timeout. Devuelve el resultado de negocio (existe / no existe)
    /// o lanza <see cref="DepartamentosNoDisponibleException"/>, que es lo que el circuit breaker cuenta.
    /// </summary>
    private async Task<bool> IntentarAsync(string departamentoId, CancellationToken cancellationToken)
    {
        using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeoutCts.CancelAfter(_options.TimeoutLlamada);

        try
        {
            using var response = await httpClient.GetAsync(
                $"departamentos/{Uri.EscapeDataString(departamentoId)}",
                HttpCompletionOption.ResponseHeadersRead,
                timeoutCts.Token);

            if (response.StatusCode == HttpStatusCode.NotFound)
            {
                return false;   // caso de negocio: el departamento no existe (no es un fallo del servicio)
            }

            if (response.IsSuccessStatusCode)
            {
                return true;
            }

            if (IsTransientFailure(response.StatusCode))
            {
                throw new DepartamentosNoDisponibleException(
                    $"Departamentos respondió {(int)response.StatusCode}.");
            }

            throw new FalloNoReintentableException(
                $"El servicio de departamentos respondió {(int)response.StatusCode}, " +
                "una respuesta no esperada para la verificación del departamento.");
        }
        catch (OperationCanceledException ex) when (!cancellationToken.IsCancellationRequested)
        {
            // Timeout propio (no una cancelación del cliente HTTP entrante).
            throw new DepartamentosNoDisponibleException(
                $"Timeout de {_options.TimeoutLlamada.TotalSeconds:0.#} s consultando departamentos.", ex);
        }
        catch (HttpRequestException ex)
        {
            // DNS, conexión rechazada, socket cerrado: el servicio no está arriba.
            throw new DepartamentosNoDisponibleException("No se pudo contactar a departamentos.", ex);
        }
    }

    private Task BackoffAsync(int attempt, CancellationToken cancellationToken)
        => Task.Delay(_options.EsperaBaseReintento * Math.Pow(2, attempt - 1), cancellationToken);

    private static bool IsTransientFailure(HttpStatusCode statusCode)
        => statusCode == HttpStatusCode.RequestTimeout
            || statusCode == HttpStatusCode.TooManyRequests
            || (int)statusCode >= 500;

    /// <summary>
    /// Fallo de un intento que NO debe reintentarse (respuesta 4xx inesperada). Hereda de
    /// <see cref="DepartamentosNoDisponibleException"/> para que el breaker también lo cuente como fallo
    /// (la dependencia responde mal) y el fallback lo trate igual, pero el bucle de reintentos lo relanza.
    /// </summary>
    private sealed class FalloNoReintentableException(string message) : DepartamentosNoDisponibleException(message);
}
