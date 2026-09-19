using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Options;
using RegistroService.Infrastructure.Departamentos;
using Xunit;

namespace RegistroService.Tests;

/// <summary>
/// Pruebas de la validación de los parámetros del circuit breaker: el reto exige 3 a 5 fallos
/// consecutivos y un circuito abierto de 30 a 60 s.
/// </summary>
public sealed class DepartamentosResilienceOptionsTests
{
    [Fact]
    public void ValoresPorDefecto_CumplenLoQueExigeElReto()
    {
        var opciones = new DepartamentosResilienceOptions();

        Assert.True(opciones.EsValida());
        Assert.Equal(3, opciones.FallosConsecutivos);
        Assert.Equal(TimeSpan.FromSeconds(5), opciones.TimeoutLlamada);
        Assert.Equal(TimeSpan.FromSeconds(30), opciones.DuracionCircuitoAbierto);
    }

    [Theory]
    [InlineData(3, 30, true)]   // límites inferiores válidos
    [InlineData(5, 60, true)]   // límites superiores válidos
    [InlineData(2, 30, false)]  // menos de 3 fallos
    [InlineData(6, 30, false)]  // más de 5 fallos
    [InlineData(3, 29, false)]  // circuito abierto menos de 30 s
    [InlineData(3, 61, false)]  // circuito abierto más de 60 s
    public void EsValida_RespetaLosRangosDelReto(int fallos, int segundosAbierto, bool esperado)
    {
        var opciones = new DepartamentosResilienceOptions
        {
            FallosConsecutivos = fallos,
            DuracionCircuitoAbierto = TimeSpan.FromSeconds(segundosAbierto)
        };

        Assert.Equal(esperado, opciones.EsValida());
    }

    [Fact]
    public void ConfiguracionInvalida_ImpideArrancarElServicio()
    {
        using var factory = new EmpleadoWebApplicationFactory().WithWebHostBuilder(builder =>
            builder.UseSetting("Departamentos:Resilience:FallosConsecutivos", "10"));

        var error = Record.Exception(() => factory.CreateClient());

        Assert.NotNull(error);
        Assert.Contains(CadenaDeExcepciones(error!), e => e is OptionsValidationException);
    }

    private static IEnumerable<Exception> CadenaDeExcepciones(Exception? excepcion)
    {
        for (var actual = excepcion; actual is not null; actual = actual.InnerException)
        {
            yield return actual;
        }
    }
}
