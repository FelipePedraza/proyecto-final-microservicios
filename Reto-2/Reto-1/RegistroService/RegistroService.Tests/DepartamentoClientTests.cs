using System.Net;
using System.Net.Http;
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

        var sut = new DepartamentoClient(client);

        var result = await sut.ExisteAsync("DEP-123");

        Assert.True(result);
        Assert.Equal(2, attempts);
    }

    private sealed class DelegatingHandlerStub(Func<HttpResponseMessage> responseFactory) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
            => Task.FromResult(responseFactory());
    }
}
