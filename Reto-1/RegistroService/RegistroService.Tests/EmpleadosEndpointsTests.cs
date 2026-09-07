using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using System.Collections.Concurrent;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Infrastructure;
using RegistroService.Infrastructure.Departamentos;
using RegistroService.Infrastructure.Persistence;
using RegistroService.Domain.Entities;
using RegistroService.Domain.Exceptions;
using RegistroService.Domain.Repositories;
using Xunit;

namespace RegistroService.Tests;

public sealed class EmpleadoWebApplicationFactory : WebApplicationFactory<Program>
{
    protected override void ConfigureWebHost(
        Microsoft.AspNetCore.Hosting.IWebHostBuilder builder)
    {
        builder.ConfigureServices(services =>
        {
            services.RemoveAll<IEmpleadoRepository>();
            services.AddSingleton<IEmpleadoRepository, TestEmpleadoRepository>();
            services.RemoveAll<IDepartamentoClient>();
            services.AddSingleton<IDepartamentoClient, DepartamentoClientFake>();
        });
        builder.UseSetting("environment", "Testing");
    }
}

internal sealed class TestEmpleadoRepository : IEmpleadoRepository
{
    private readonly ConcurrentDictionary<string, Empleado> empleados = new();
    private readonly object sync = new();

    public Task<Empleado?> ObtenerPorIdAsync(
        string id,
        CancellationToken cancellationToken = default)
        => Task.FromResult(empleados.TryGetValue(id.Trim(), out var empleado) ? empleado : null);

    public Task<bool> ExisteEmailAsync(
        string email,
        CancellationToken cancellationToken = default)
        => Task.FromResult(empleados.Values.Any(e =>
            e.Email.Equals(email.Trim(), StringComparison.OrdinalIgnoreCase)));

    public Task<bool> ExisteNumeroEmpleadoAsync(
        string numeroEmpleado,
        CancellationToken cancellationToken = default)
        => Task.FromResult(empleados.Values.Any(e => e.NumeroEmpleado == numeroEmpleado.Trim()));

    public Task RegistrarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default)
    {
        lock (sync)
        {
            if (empleados.Values.Any(e =>
                e.Email.Equals(empleado.Email, StringComparison.OrdinalIgnoreCase)))
            {
                throw new EmpleadoDuplicadoException("email", empleado.Email);
            }

            if (empleados.Values.Any(e => e.NumeroEmpleado == empleado.NumeroEmpleado))
            {
                throw new EmpleadoDuplicadoException("numeroEmpleado", empleado.NumeroEmpleado);
            }

            if (!empleados.TryAdd(empleado.Id, empleado))
            {
                throw new EmpleadoDuplicadoException("id", empleado.Id);
            }
        }

        return Task.CompletedTask;
    }
}

internal sealed class DepartamentoClientFake : IDepartamentoClient
{
    public Task<bool> ExisteAsync(
        string departamentoId,
        CancellationToken cancellationToken = default) => Task.FromResult(true);
}

public sealed class EmpleadosEndpointsTests : IClassFixture<EmpleadoWebApplicationFactory>
{
    private readonly HttpClient _client;

    public EmpleadosEndpointsTests(EmpleadoWebApplicationFactory factory)
    {
        _client = factory.CreateClient();
    }

    [Fact]
    public async Task RegistrarYConsultar_ConservaModeloCanonico()
    {
        var id = $"E-{Guid.NewGuid():N}";
        var request = CrearEmpleado(id, $"{id}@empresa.com", $"EMP-{id}");

        var postResponse = await _client.PostAsJsonAsync("/empleados", request);

        Assert.Equal(HttpStatusCode.OK, postResponse.StatusCode);
        using var postJson = JsonDocument.Parse(await postResponse.Content.ReadAsStringAsync());
        Assert.Equal(id, postJson.RootElement.GetProperty("id").GetString());
        Assert.Equal("ACTIVO", postJson.RootElement.GetProperty("estado").GetString());

        var getResponse = await _client.GetAsync($"/empleados/{id}");

        Assert.Equal(HttpStatusCode.OK, getResponse.StatusCode);
        using var getJson = JsonDocument.Parse(await getResponse.Content.ReadAsStringAsync());
        Assert.Equal(id, getJson.RootElement.GetProperty("id").GetString());
    }

    [Fact]
    public async Task ConsultarEmpleadoInexistente_RetornaMensajeExacto()
    {
        const string id = "NO-EXISTE";

        var response = await _client.GetAsync($"/empleados/{id}");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal(
            $"El empleado con id {id} no existe",
            await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task RegistrarEmailDuplicado_RetornaBadRequest()
    {
        var suffix = Guid.NewGuid().ToString("N");
        var email = $"duplicado-{suffix}@empresa.com";
        await _client.PostAsJsonAsync(
            "/empleados",
            CrearEmpleado($"E-A-{suffix}", email, $"EMP-A-{suffix}"));

        var response = await _client.PostAsJsonAsync(
            "/empleados",
            CrearEmpleado($"E-B-{suffix}", email.ToUpperInvariant(), $"EMP-B-{suffix}"));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Contains("email", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task RegistrarNumeroEmpleadoDuplicado_RetornaBadRequest()
    {
        var suffix = Guid.NewGuid().ToString("N");
        var numeroEmpleado = $"EMP-DUP-{suffix}";
        await _client.PostAsJsonAsync(
            "/empleados",
            CrearEmpleado($"E-A-{suffix}", $"a-{suffix}@empresa.com", numeroEmpleado));

        var response = await _client.PostAsJsonAsync(
            "/empleados",
            CrearEmpleado($"E-B-{suffix}", $"b-{suffix}@empresa.com", numeroEmpleado));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Contains("numeroEmpleado", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task RegistrarCampoVacio_RetornaBadRequest()
    {
        var suffix = Guid.NewGuid().ToString("N");
        var request = CrearEmpleado(
            $"E-{suffix}",
            $"vacio-{suffix}@empresa.com",
            $"EMP-{suffix}") with
        {
            Nombre = " "
        };

        var response = await _client.PostAsJsonAsync("/empleados", request);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Contains("obligatorio", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task RutaOMetodoNoSoportado_Retorna404ConMensajeExacto()
    {
        var rutaResponse = await _client.GetAsync("/otra-ruta");
        var metodoResponse = await _client.PutAsync("/empleados", null);

        Assert.Equal(HttpStatusCode.NotFound, rutaResponse.StatusCode);
        Assert.Equal("Recurso no encontrado", await rutaResponse.Content.ReadAsStringAsync());
        Assert.Equal(HttpStatusCode.NotFound, metodoResponse.StatusCode);
        Assert.Equal("Recurso no encontrado", await metodoResponse.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task RegistrosConcurrentes_NoPermitenEmailDuplicado()
    {
        var suffix = Guid.NewGuid().ToString("N");
        var email = $"concurrente-{suffix}@empresa.com";
        var solicitudes = Enumerable.Range(1, 12)
            .Select(i => _client.PostAsJsonAsync(
                "/empleados",
                CrearEmpleado($"E-{suffix}-{i}", email, $"EMP-{suffix}-{i}")));

        var responses = await Task.WhenAll(solicitudes);

        Assert.Single(responses.Where(r => r.StatusCode == HttpStatusCode.OK));
        Assert.Equal(11, responses.Count(r => r.StatusCode == HttpStatusCode.BadRequest));
    }

    private static CrearEmpleadoRequest CrearEmpleado(
        string id,
        string email,
        string numeroEmpleado) => new(
            id,
            "Juan",
            "Pérez",
            email,
            numeroEmpleado,
            "Desarrollador Senior",
            "Tecnología",
            "IT",
            new DateOnly(2026, 2, 10));

    private sealed record CrearEmpleadoRequest(
        string Id,
        string Nombre,
        string Apellido,
        string Email,
        string NumeroEmpleado,
        string Cargo,
        string Area,
        string DepartamentoId,
        DateOnly FechaIngreso);
}
