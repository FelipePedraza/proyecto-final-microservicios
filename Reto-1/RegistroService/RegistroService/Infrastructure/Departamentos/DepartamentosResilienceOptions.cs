namespace RegistroService.Infrastructure.Departamentos;

/// <summary>
/// Parámetros de resiliencia de la llamada síncrona a DepartamentosService.
/// Se enlazan desde la sección <c>Departamentos:Resilience</c> de la configuración.
/// </summary>
public sealed class DepartamentosResilienceOptions
{
    public const string SectionName = "Departamentos:Resilience";

    /// <summary>Fallos consecutivos que abren el circuito (el reto exige entre 3 y 5).</summary>
    public int FallosConsecutivos { get; set; } = 3;

    /// <summary>Timeout de cada intento de llamada HTTP a Departamentos.</summary>
    public TimeSpan TimeoutLlamada { get; set; } = TimeSpan.FromSeconds(5);

    /// <summary>Tiempo que el circuito permanece OPEN antes de pasar a HALF_OPEN (el reto exige entre 30 y 60 s).</summary>
    public TimeSpan DuracionCircuitoAbierto { get; set; } = TimeSpan.FromSeconds(30);

    /// <summary>Espera base del backoff exponencial entre reintentos (1 s, 2 s, ...).</summary>
    public TimeSpan EsperaBaseReintento { get; set; } = TimeSpan.FromSeconds(1);

    /// <summary>
    /// Indica si los valores respetan lo que exige el reto: 3 a 5 fallos consecutivos y un
    /// circuito abierto entre 30 y 60 s. Se evalúa al arrancar (<c>ValidateOnStart</c>): con una
    /// configuración inválida el servicio no inicia, en lugar de correr con un breaker mal calibrado.
    /// </summary>
    public bool EsValida()
        => FallosConsecutivos is >= 3 and <= 5
           && DuracionCircuitoAbierto >= TimeSpan.FromSeconds(30)
           && DuracionCircuitoAbierto <= TimeSpan.FromSeconds(60)
           && TimeoutLlamada > TimeSpan.Zero
           && EsperaBaseReintento >= TimeSpan.Zero;
}
