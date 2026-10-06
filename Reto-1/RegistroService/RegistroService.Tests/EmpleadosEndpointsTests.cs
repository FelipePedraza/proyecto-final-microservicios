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
using RegistroService.Infrastructure.Messaging;
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
            services.RemoveAll<IEventPublisher>();
            services.AddSingleton<IEventPublisher, EndpointNoOpEventPublisher>();
        });
        builder.UseSetting("environment", "Testing");
    }
}

internal sealed class EndpointNoOpEventPublisher : IEventPublisher
{
    public void Publish<T>(string eventType, T data) { }
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

    public Task ActualizarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default)
    {
        empleados[empleado.Id] = empleado;
        return Task.CompletedTask;
    }

    public Task<IEnumerable<Empleado>> ObtenerRetiradosAsync(
        DateTime? desde,
        DateTime? hasta,
        CancellationToken cancellationToken = default)
        => Task.FromResult<IEnumerable<Empleado>>(empleados.Values
            .Where(e => e.Estado == RegistroService.Domain.Enums.EstadoEmpleado.Retirado)
            .Where(e => desde is null || (e.FechaRetiro.HasValue && e.FechaRetiro.Value >= desde.Value))
            .Where(e => hasta is null || (e.FechaRetiro.HasValue && e.FechaRetiro.Value <= hasta.Value))
            .ToArray());
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
    public async Task SwaggerDocument_DescribeEndpointSecurityForGateway()
    {
        var response = await _client.GetAsync("/swagger/v1/swagger.json");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        var root = document.RootElement;
        var paths = root.GetProperty("paths");

        Assert.Equal(
            new[]
            {
                "/health",
                "/health/ready",
                "/health/circuit-breaker",
                "/empleados",
                "/empleados/{id}"
            }.OrderBy(path => path),
            paths.EnumerateObject().Select(path => path.Name).OrderBy(path => path));
        AssertOperation(paths, "/health", "get", "200");
        AssertOperation(paths, "/health/ready", "get", "200", "503");
        AssertOperation(paths, "/health/circuit-breaker", "get", "200");
        AssertOperation(paths, "/empleados", "post", "201", "400", "409", "503", "500");
        AssertOperation(paths, "/empleados", "get", "200", "400", "503", "500");
        AssertOperation(paths, "/empleados/{id}", "get", "200", "404", "503", "500");
        AssertOperation(paths, "/empleados/{id}", "put", "200", "400", "404", "503", "500");
        AssertOperation(paths, "/empleados/{id}", "delete", "204", "404", "503", "500");
        AssertBearerSecurity(paths.GetProperty("/empleados").GetProperty("post"));
        AssertBearerSecurity(paths.GetProperty("/empleados").GetProperty("get"));
        AssertBearerSecurity(paths.GetProperty("/empleados/{id}").GetProperty("get"));
        AssertBearerSecurity(paths.GetProperty("/empleados/{id}").GetProperty("put"));
        AssertBearerSecurity(paths.GetProperty("/empleados/{id}").GetProperty("delete"));
        Assert.False(paths.GetProperty("/health").GetProperty("get").TryGetProperty("security", out _));
        Assert.False(paths.GetProperty("/health/ready").GetProperty("get").TryGetProperty("security", out _));
        Assert.False(paths.GetProperty("/health/circuit-breaker").GetProperty("get").TryGetProperty("security", out _));

        var bearerScheme = root.GetProperty("components").GetProperty("securitySchemes").GetProperty("Bearer");
        Assert.Equal("http", bearerScheme.GetProperty("type").GetString());
        Assert.Equal("bearer", bearerScheme.GetProperty("scheme").GetString());
        Assert.Equal("JWT", bearerScheme.GetProperty("bearerFormat").GetString());

        var createOperation = paths.GetProperty("/empleados").GetProperty("post");
        Assert.Equal(
            "application/json",
            createOperation.GetProperty("requestBody").GetProperty("content")
                .EnumerateObject().Single().Name);
        AssertResponseSchema(createOperation, "201", "EmpleadoResponse");
        AssertResponseSchema(createOperation, "400", "ErrorResponse");
        AssertResponseSchema(
            paths.GetProperty("/empleados/{id}").GetProperty("get"),
            "200",
            "EmpleadoResponse");
        AssertResponseSchema(
            paths.GetProperty("/empleados/{id}").GetProperty("get"),
            "404",
            "ErrorResponse");

        var listOperation = paths.GetProperty("/empleados").GetProperty("get");
        var queryParameters = listOperation.GetProperty("parameters")
            .EnumerateArray()
            .Where(parameter => parameter.GetProperty("in").GetString() == "query")
            .Select(parameter => parameter.GetProperty("name").GetString())
            .ToHashSet();
        Assert.Contains("estado", queryParameters);
        Assert.Contains("desde", queryParameters);
        Assert.Contains("hasta", queryParameters);

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

    private static void AssertOperation(
        JsonElement paths,
        string path,
        string method,
        params string[] expectedStatusCodes)
    {
        var operation = paths.GetProperty(path).GetProperty(method);
        var responses = operation.GetProperty("responses");

        foreach (var statusCode in expectedStatusCodes)
        {
            Assert.True(responses.TryGetProperty(statusCode, out _), $"{method.ToUpperInvariant()} {path} should document {statusCode}.");
        }
    }

    private static void AssertResponseSchema(JsonElement operation, string statusCode, string schemaName)
    {
        var response = operation.GetProperty("responses").GetProperty(statusCode);
        Assert.Equal(
            $"#/components/schemas/{schemaName}",
            response.GetProperty("content").GetProperty("application/json")
                .GetProperty("schema").GetProperty("$ref").GetString());
    }

    private static void AssertBearerSecurity(JsonElement operation)
    {
        var requirements = operation.GetProperty("security").EnumerateArray().ToArray();
        Assert.Contains(requirements, requirement =>
            requirement.TryGetProperty("Bearer", out var scopes)
            && scopes.ValueKind == JsonValueKind.Array
            && !scopes.EnumerateArray().Any());
    }

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
