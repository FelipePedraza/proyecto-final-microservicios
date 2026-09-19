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
    /// <summary>Id de departamento con el que el fake simula que DepartamentosService está caído.</summary>
    public const string DepartamentoCaido = "DEP-CAIDO";

    public Task<bool> ExisteAsync(
        string departamentoId,
        CancellationToken cancellationToken = default)
        => departamentoId == DepartamentoCaido
            ? throw new DepartamentosNoDisponibleException("departamentos caído")
            : Task.FromResult(true);
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

        Assert.Equal(HttpStatusCode.Created, postResponse.StatusCode);
        Assert.Equal($"/empleados/{id}", postResponse.Headers.Location?.ToString());
        using var postJson = JsonDocument.Parse(await postResponse.Content.ReadAsStringAsync());
        Assert.Equal(id, postJson.RootElement.GetProperty("id").GetString());
        Assert.Equal("ACTIVO", postJson.RootElement.GetProperty("estado").GetString());

        var getResponse = await _client.GetAsync($"/empleados/{id}");

        Assert.Equal(HttpStatusCode.OK, getResponse.StatusCode);
        using var getJson = JsonDocument.Parse(await getResponse.Content.ReadAsStringAsync());
        Assert.Equal(id, getJson.RootElement.GetProperty("id").GetString());
    }

    /// <summary>
    /// Fallback del Reto 3 de extremo a extremo por HTTP: con Departamentos caído el registro NO falla,
    /// responde 201 con estado PENDIENTE_VALIDACION y el empleado queda consultable con ese estado.
    /// </summary>
    [Fact]
    public async Task Registrar_ConDepartamentosNoDisponible_AplicaFallbackPendienteValidacion()
    {
        var id = $"E-{Guid.NewGuid():N}";
        var request = CrearEmpleado(id, $"{id}@empresa.com", $"EMP-{id}")
            with { DepartamentoId = DepartamentoClientFake.DepartamentoCaido };

        var postResponse = await _client.PostAsJsonAsync("/empleados", request);

        Assert.Equal(HttpStatusCode.Created, postResponse.StatusCode);
        using var postJson = JsonDocument.Parse(await postResponse.Content.ReadAsStringAsync());
        Assert.Equal("PENDIENTE_VALIDACION", postJson.RootElement.GetProperty("estado").GetString());

        var getResponse = await _client.GetAsync($"/empleados/{id}");
        using var getJson = JsonDocument.Parse(await getResponse.Content.ReadAsStringAsync());
        Assert.Equal("PENDIENTE_VALIDACION", getJson.RootElement.GetProperty("estado").GetString());
    }

    /// <summary>El endpoint de observabilidad expone el estado y la configuración vigente del circuito.</summary>
    [Fact]
    public async Task HealthCircuitBreaker_ReportaEstadoDelCircuito()
    {
        var response = await _client.GetAsync("/health/circuit-breaker");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        Assert.Equal("CLOSED", json.RootElement.GetProperty("estado").GetString());
        Assert.Equal(3, json.RootElement.GetProperty("fallosConsecutivos").GetInt32());
        Assert.Equal(30, json.RootElement.GetProperty("duracionCircuitoAbiertoSegundos").GetDouble());
    }

    [Fact]
    public async Task ConsultarEmpleadoInexistente_RetornaMensajeExacto()
    {
        const string id = "NO-EXISTE";

        var response = await _client.GetAsync($"/empleados/{id}");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        Assert.Equal($"El empleado con id {id} no existe", json.RootElement.GetProperty("error").GetString());
    }

    [Fact]
    public async Task RegistrarEmailDuplicado_RetornaConflict()
    {
        var suffix = Guid.NewGuid().ToString("N");
        var email = $"duplicado-{suffix}@empresa.com";
        await _client.PostAsJsonAsync(
            "/empleados",
            CrearEmpleado($"E-A-{suffix}", email, $"EMP-A-{suffix}"));

        var response = await _client.PostAsJsonAsync(
            "/empleados",
            CrearEmpleado($"E-B-{suffix}", email.ToUpperInvariant(), $"EMP-B-{suffix}"));

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Contains("email", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task RegistrarNumeroEmpleadoDuplicado_RetornaConflict()
    {
        var suffix = Guid.NewGuid().ToString("N");
        var numeroEmpleado = $"EMP-DUP-{suffix}";
        await _client.PostAsJsonAsync(
            "/empleados",
            CrearEmpleado($"E-A-{suffix}", $"a-{suffix}@empresa.com", numeroEmpleado));

        var response = await _client.PostAsJsonAsync(
            "/empleados",
            CrearEmpleado($"E-B-{suffix}", $"b-{suffix}@empresa.com", numeroEmpleado));

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
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
        using var rutaJson = JsonDocument.Parse(await rutaResponse.Content.ReadAsStringAsync());
        Assert.Equal("Recurso no encontrado", rutaJson.RootElement.GetProperty("error").GetString());

        Assert.Equal(HttpStatusCode.NotFound, metodoResponse.StatusCode);
        using var metodoJson = JsonDocument.Parse(await metodoResponse.Content.ReadAsStringAsync());
        Assert.Equal("Recurso no encontrado", metodoJson.RootElement.GetProperty("error").GetString());
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

        Assert.Single(responses.Where(r => r.StatusCode == HttpStatusCode.Created));
        Assert.Equal(11, responses.Count(r => r.StatusCode == HttpStatusCode.Conflict));
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
