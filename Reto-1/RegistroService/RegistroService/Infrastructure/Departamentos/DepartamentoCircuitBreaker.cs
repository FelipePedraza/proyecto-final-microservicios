using Microsoft.Extensions.Options;
using Polly;
using Polly.CircuitBreaker;

namespace RegistroService.Infrastructure.Departamentos;

/// <summary>
/// Circuit breaker compartido (singleton) para las llamadas a DepartamentosService.
///
/// Estados:
///   CLOSED    - las llamadas pasan; se cuentan los fallos consecutivos.
///   OPEN      - tras N fallos consecutivos las llamadas se rechazan sin tocar la red
///               durante <see cref="DepartamentosResilienceOptions.DuracionCircuitoAbierto"/>.
///   HALF_OPEN - vencido ese tiempo se permite UNA llamada de prueba: si tiene éxito el
///               circuito vuelve a CLOSED; si falla, vuelve a OPEN.
///
/// Un "fallo" es un INTENTO HTTP fallido (<see cref="DepartamentosNoDisponibleException"/>:
/// timeout, conexión rechazada, 408/429/5xx). Se cuenta por intento, no por petición del
/// usuario, para que el circuito se abra aunque el cliente (p. ej. el gateway) corte la
/// petición antes de que se agoten los reintentos. Un 404 (el departamento no existe) es
/// una respuesta de negocio válida y cuenta como éxito.
/// </summary>
public sealed class DepartamentoCircuitBreaker
{
    private readonly AsyncCircuitBreakerPolicy _policy;

    public DepartamentoCircuitBreaker(
        IOptions<DepartamentosResilienceOptions> options,
        ILogger<DepartamentoCircuitBreaker> logger)
    {
        var config = options.Value;
        FallosConsecutivos = config.FallosConsecutivos;
        DuracionCircuitoAbierto = config.DuracionCircuitoAbierto;

        // Se usa la API clásica de Polly (Policy.Handle...CircuitBreakerAsync), incluida en el paquete
        // Polly 8.x, porque su semántica es exactamente la del reto: "N fallos CONSECUTIVOS".
        // La API nueva (ResiliencePipeline) modela el umbral como ratio de fallos dentro de una ventana
        // de tiempo, no como "N fallos seguidos"; aquí se prefirió la equivalencia literal con el reto.
        _policy = Policy
            .Handle<DepartamentosNoDisponibleException>()
            .CircuitBreakerAsync(
                exceptionsAllowedBeforeBreaking: config.FallosConsecutivos,
                durationOfBreak: config.DuracionCircuitoAbierto,
                onBreak: (exception, duracion) => logger.LogWarning(
                    "Circuito de departamentos: CLOSED -> OPEN tras {Fallos} fallos consecutivos. " +
                    "Rechazará llamadas durante {Segundos} s. Último error: {Error}",
                    config.FallosConsecutivos, duracion.TotalSeconds, exception.Message),
                onReset: () => logger.LogInformation(
                    "Circuito de departamentos: HALF_OPEN -> CLOSED. El servicio respondió, se reanuda el tráfico normal."),
                onHalfOpen: () => logger.LogInformation(
                    "Circuito de departamentos: OPEN -> HALF_OPEN. Se permite una llamada de prueba."));
    }

    /// <summary>Umbral configurado de fallos consecutivos que abre el circuito.</summary>
    public int FallosConsecutivos { get; }

    /// <summary>Tiempo configurado que el circuito permanece abierto antes de pasar a HALF_OPEN.</summary>
    public TimeSpan DuracionCircuitoAbierto { get; }

    /// <summary>
    /// Estado actual del circuito: CLOSED, OPEN o HALF_OPEN. Al consultarlo, un circuito OPEN cuyo
    /// tiempo ya venció se reporta como HALF_OPEN (Polly hace la transición al leer el estado).
    /// </summary>
    public string Estado => _policy.CircuitState switch
    {
        CircuitState.Closed => "CLOSED",
        CircuitState.HalfOpen => "HALF_OPEN",
        _ => "OPEN"
    };

    /// <summary>
    /// Ejecuta la operación protegida. Si el circuito está OPEN (o HALF_OPEN con una
    /// prueba en curso) lanza <see cref="BrokenCircuitException"/> sin ejecutarla.
    /// </summary>
    public Task<T> EjecutarAsync<T>(
        Func<CancellationToken, Task<T>> operacion,
        CancellationToken cancellationToken)
        => _policy.ExecuteAsync(operacion, cancellationToken);
}
