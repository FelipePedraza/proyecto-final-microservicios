using System.Net;
using System.Net.Http;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;
using RegistroService.Infrastructure.Departamentos;
using Xunit;

namespace RegistroService.Tests;

/// <summary>
/// Pruebas de <see cref="DepartamentoClient"/> con el circuit breaker REAL de Polly y un
/// <see cref="HttpMessageHandler"/> falso que simula las respuestas de DepartamentosService.
/// Verifican los reintentos y la máquina de estados CLOSED -> OPEN -> HALF_OPEN -> CLOSED / OPEN.
/// </summary>
public sealed class DepartamentoClientTests
{
    [Fact]
    public async Task ExisteAsync_Retries_When_DepartamentoService_IsTransientlyUnavailable()
    {
        var attempts = 0;
        var (sut, _) = CrearCliente(() =>
        {
            attempts++;
            return attempts == 1
                ? new HttpResponseMessage(HttpStatusCode.ServiceUnavailable)
                : new HttpResponseMessage(HttpStatusCode.OK);
        });

        var result = await sut.ExisteAsync("DEP-123");

        Assert.True(result);
        Assert.Equal(2, attempts);
    }

    [Fact]
    public async Task ExisteAsync_Lanza_NoDisponible_Cuando_Departamentos_Responde_503_Siempre()
    {
        var attempts = 0;
        var (sut, breaker) = CrearCliente(() =>
        {
            attempts++;
            return new HttpResponseMessage(HttpStatusCode.ServiceUnavailable);
        });

        await Assert.ThrowsAsync<DepartamentosNoDisponibleException>(() => sut.ExisteAsync("IT"));

        Assert.Equal(3, attempts);
        Assert.Equal("OPEN", breaker.Estado); // 3 intentos fallidos consecutivos abren el circuito
    }

    [Fact]
    public async Task ExisteAsync_Lanza_NoDisponible_Cuando_No_Hay_Conexion()
    {
        var (sut, _) = CrearCliente(() => throw new HttpRequestException("connection refused"));

        await Assert.ThrowsAsync<DepartamentosNoDisponibleException>(() => sut.ExisteAsync("IT"));
    }

    // -------------------------------------------------------------------------
    // Circuit breaker: CLOSED -> OPEN -> HALF_OPEN -> CLOSED / OPEN
    // Los fallos se cuentan POR INTENTO HTTP, no por petición del usuario.
    // -------------------------------------------------------------------------

    [Fact]
    public async Task Circuito_Se_Abre_Tras_Fallos_Consecutivos_Y_Rechaza_Sin_Llamar_Al_Servicio()
    {
        var attempts = 0;
        var (sut, breaker) = CrearCliente(() =>
        {
            attempts++;
            return new HttpResponseMessage(HttpStatusCode.ServiceUnavailable);
        });
        Assert.Equal("CLOSED", breaker.Estado);

        await Assert.ThrowsAsync<DepartamentosNoDisponibleException>(() => sut.ExisteAsync("IT"));

        Assert.Equal("OPEN", breaker.Estado);
        Assert.Equal(3, attempts);

        // Con el circuito OPEN el rechazo es inmediato: no se toca la red.
        await Assert.ThrowsAsync<DepartamentosCircuitoAbiertoException>(() => sut.ExisteAsync("IT"));
        Assert.Equal(3, attempts);
    }

    [Fact]
    public async Task Circuito_Se_Abre_A_Mitad_De_Los_Reintentos_Y_Deja_De_Reintentar()
    {
        var attempts = 0;
        var (sut, breaker) = CrearCliente(
            () =>
            {
                attempts++;
                return new HttpResponseMessage(HttpStatusCode.ServiceUnavailable);
            },
            fallosConsecutivos: 5);

        // 1ª petición: 3 intentos fallidos (2 fallos aún por debajo del umbral de 5).
        await Assert.ThrowsAsync<DepartamentosNoDisponibleException>(() => sut.ExisteAsync("IT"));
        Assert.Equal(3, attempts);
        Assert.Equal("CLOSED", breaker.Estado);

        // 2ª petición: el 5º fallo consecutivo (2º intento) abre el circuito y el 3er intento
        // ya no se ejecuta: se rechaza por circuito abierto.
        await Assert.ThrowsAsync<DepartamentosCircuitoAbiertoException>(() => sut.ExisteAsync("IT"));
        Assert.Equal(5, attempts);
        Assert.Equal("OPEN", breaker.Estado);
    }

    [Fact]
    public async Task Un_Exito_Reinicia_El_Contador_De_Fallos_Consecutivos()
    {
        // 2 fallos + éxito, dos veces: 4 fallos en total pero nunca 3 seguidos.
        var respuestas = new Queue<HttpStatusCode>([
            HttpStatusCode.ServiceUnavailable, HttpStatusCode.ServiceUnavailable, HttpStatusCode.OK,
            HttpStatusCode.ServiceUnavailable, HttpStatusCode.ServiceUnavailable, HttpStatusCode.OK]);
        var (sut, breaker) = CrearCliente(() => new HttpResponseMessage(respuestas.Dequeue()));

        Assert.True(await sut.ExisteAsync("IT"));
        Assert.True(await sut.ExisteAsync("IT"));

        Assert.Equal("CLOSED", breaker.Estado);
    }

    [Fact]
    public async Task Departamento_Inexistente_404_No_Cuenta_Como_Fallo_Del_Circuito()
    {
        var (sut, breaker) = CrearCliente(() => new HttpResponseMessage(HttpStatusCode.NotFound));

        for (var i = 0; i < 6; i++)
        {
            Assert.False(await sut.ExisteAsync("NO-EXISTE"));
        }

        Assert.Equal("CLOSED", breaker.Estado);
    }

    [Fact]
    public async Task HalfOpen_Cierra_El_Circuito_Si_La_Llamada_De_Prueba_Tiene_Exito()
    {
        var fallar = true;
        var (sut, breaker) = CrearCliente(
            () => new HttpResponseMessage(fallar ? HttpStatusCode.ServiceUnavailable : HttpStatusCode.OK),
            duracionCircuito: TimeSpan.FromMilliseconds(400));

        await AbrirCircuitoAsync(sut, breaker);
        await Task.Delay(TimeSpan.FromMilliseconds(600));
        Assert.Equal("HALF_OPEN", breaker.Estado);

        fallar = false; // Departamentos se recuperó
        Assert.True(await sut.ExisteAsync("IT"));

        Assert.Equal("CLOSED", breaker.Estado);
    }

    [Fact]
    public async Task HalfOpen_Vuelve_A_Abrir_El_Circuito_Si_La_Llamada_De_Prueba_Falla()
    {
        var attempts = 0;
        var (sut, breaker) = CrearCliente(
            () =>
            {
                attempts++;
                return new HttpResponseMessage(HttpStatusCode.ServiceUnavailable);
            },
            duracionCircuito: TimeSpan.FromMilliseconds(400));

        await AbrirCircuitoAsync(sut, breaker);
        await Task.Delay(TimeSpan.FromMilliseconds(600));
        Assert.Equal("HALF_OPEN", breaker.Estado);

        // Se ejecuta UNA sola llamada de prueba (llega a la red), falla, el circuito vuelve a OPEN
        // y el reintento siguiente ya es rechazado sin tocar la red.
        var intentosAntes = attempts;
        await Assert.ThrowsAsync<DepartamentosCircuitoAbiertoException>(() => sut.ExisteAsync("IT"));

        Assert.Equal(intentosAntes + 1, attempts);
        Assert.Equal("OPEN", breaker.Estado);
    }

    private static async Task AbrirCircuitoAsync(DepartamentoClient cliente, DepartamentoCircuitBreaker breaker)
    {
        await Assert.ThrowsAsync<DepartamentosNoDisponibleException>(() => cliente.ExisteAsync("IT"));

        Assert.Equal("OPEN", breaker.Estado);
    }

    /// <summary>
    /// Crea un cliente con el circuit breaker real de Polly. La espera de reintentos es de 1 ms para
    /// que las pruebas sean rápidas; el resto de parámetros coincide con los valores del reto.
    /// </summary>
    private static (DepartamentoClient Cliente, DepartamentoCircuitBreaker Breaker) CrearCliente(
        Func<HttpResponseMessage> respuesta,
        TimeSpan? duracionCircuito = null,
        int fallosConsecutivos = 3)
    {
        var options = Options.Create(new DepartamentosResilienceOptions
        {
            FallosConsecutivos = fallosConsecutivos,
            TimeoutLlamada = TimeSpan.FromSeconds(5),
            DuracionCircuitoAbierto = duracionCircuito ?? TimeSpan.FromSeconds(30),
            EsperaBaseReintento = TimeSpan.FromMilliseconds(1)
        });
        var breaker = new DepartamentoCircuitBreaker(options, NullLogger<DepartamentoCircuitBreaker>.Instance);
        var httpClient = new HttpClient(new DelegatingHandlerStub(respuesta))
        {
            BaseAddress = new Uri("http://departamentos-service:8081/")
        };

        return (new DepartamentoClient(httpClient, NullLogger<DepartamentoClient>.Instance, breaker, options), breaker);
    }

    private sealed class DelegatingHandlerStub(Func<HttpResponseMessage> responseFactory) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
            => Task.FromResult(responseFactory());
    }
}
