using System.Net;
using System.Net.Http;
using Microsoft.Extensions.Logging.Abstractions;
using RegistroService.Infrastructure.Departamentos;
using Xunit;

namespace RegistroService.Tests;

public sealed class DepartamentoClientTests
{
    [Fact]
    public async Task ExisteAsync_Retries_When_DepartamentoService_IsTransientlyUnavailable()
    {
        var attempts = 0;
        var handler = new DelegatingHandlerStub(() =>
        {
            attempts++;
            return attempts == 1
                ? new HttpResponseMessage(HttpStatusCode.ServiceUnavailable)
                : new HttpResponseMessage(HttpStatusCode.OK);
        });

        var client = new HttpClient(handler)
        {
            BaseAddress = new Uri("http://departamentos-service:8081/")
        };

        var sut = new DepartamentoClient(client, NullLogger<DepartamentoClient>.Instance);

        var result = await sut.ExisteAsync("DEP-123");

        Assert.True(result);
        Assert.Equal(2, attempts);
    }

    [Fact]
    public async Task ExisteAsync_Lanza_NoDisponible_Cuando_Departamentos_Responde_503_Siempre()
    {
        var attempts = 0;
        var handler = new DelegatingHandlerStub(() =>
        {
            attempts++;
            return new HttpResponseMessage(HttpStatusCode.ServiceUnavailable);
        });

        var sut = new DepartamentoClient(
            new HttpClient(handler) { BaseAddress = new Uri("http://departamentos-service:8081/") },
            NullLogger<DepartamentoClient>.Instance);

        await Assert.ThrowsAsync<DepartamentosNoDisponibleException>(() => sut.ExisteAsync("IT"));
        Assert.Equal(3, attempts);
    }

    [Fact]
    public async Task ExisteAsync_Lanza_NoDisponible_Cuando_No_Hay_Conexion()
    {
        var handler = new DelegatingHandlerStub(() => throw new HttpRequestException("connection refused"));
        var sut = new DepartamentoClient(
            new HttpClient(handler) { BaseAddress = new Uri("http://departamentos-service:8081/") },
            NullLogger<DepartamentoClient>.Instance);

        await Assert.ThrowsAsync<DepartamentosNoDisponibleException>(() => sut.ExisteAsync("IT"));
    }

    private sealed class DelegatingHandlerStub(Func<HttpResponseMessage> responseFactory) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
            => Task.FromResult(responseFactory());
    }
}
