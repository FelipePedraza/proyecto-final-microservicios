// =============================================================================
// EmpleadoServiceTests.cs
// Pruebas unitarias del servicio de empleados (capa de dominio).
// Objetivo: verificar la lógica de negocio sin depender de HTTP ni de la API.
// Estrategia: usamos un FakeRepository que implementa IEmpleadoRepository
//             con validaciones de duplicados para simular el comportamiento real.
// =============================================================================
using EmpleadoService.Domain.Entities;
using EmpleadoService.Domain.Enums;
using EmpleadoService.Domain.Exceptions;
using EmpleadoService.Domain.Repositories;
using EmpleadoService.Domain.Services;
using EmpleadoService.Infrastructure.Departamentos;
using EmpleadoService.Infrastructure.Messaging;
using Xunit;
using EmpleadoDomainService = EmpleadoService.Domain.Services.EmpleadoService;

namespace EmpleadoService.Tests;

/// <summary>
/// Pruebas unitarias de EmpleadoService.
/// Cada [Fact] es un caso de prueba independiente.
/// </summary>
public sealed class EmpleadoServiceTests
{
    // -------------------------------------------------------------------------
    // FAKE REPOSITORY
    // -------------------------------------------------------------------------
    // Simula el comportamiento real de EmpleadoRepository, incluyendo
    // validaciones de duplicados. Así probamos el servicio en aislamiento.
    // -------------------------------------------------------------------------
    private sealed class FakeRepository : IEmpleadoRepository
    {
        private readonly Dictionary<string, Empleado> _empleados = new();
        private readonly HashSet<string> _emails = new(StringComparer.OrdinalIgnoreCase);
        private readonly HashSet<string> _numeros = new(StringComparer.Ordinal);

        public int Actualizaciones { get; private set; }

        public Task<Empleado?> ObtenerPorIdAsync(string id, CancellationToken ct = default)
        {
            _empleados.TryGetValue(id, out var e);
            return Task.FromResult(e);
        }

        public Task RegistrarAsync(Empleado empleado, CancellationToken ct = default)
        {
            // Validaciones de duplicado (igual que el repositorio real)
            if (_empleados.ContainsKey(empleado.Id))
                throw new EmpleadoDuplicadoException("id", empleado.Id);
            if (_emails.Contains(empleado.Email))
                throw new EmpleadoDuplicadoException("email", empleado.Email);
            if (_numeros.Contains(empleado.NumeroEmpleado))
                throw new EmpleadoDuplicadoException("numeroEmpleado", empleado.NumeroEmpleado);

            _empleados[empleado.Id] = empleado;
            _emails.Add(empleado.Email);
            _numeros.Add(empleado.NumeroEmpleado);
            return Task.CompletedTask;
        }

        public Task ActualizarAsync(Empleado empleado, CancellationToken ct = default)
        {
            _empleados[empleado.Id] = empleado;
            Actualizaciones++;
            return Task.CompletedTask;
        }

        public Task<IEnumerable<Empleado>> ObtenerRetiradosAsync(
            DateTime? desde,
            DateTime? hasta,
            CancellationToken ct = default)
            => Task.FromResult<IEnumerable<Empleado>>(_empleados.Values
                .Where(e => e.Estado == EstadoEmpleado.Retirado)
                .Where(e => desde is null || (e.FechaRetiro.HasValue && e.FechaRetiro.Value >= desde.Value))
                .Where(e => hasta is null || (e.FechaRetiro.HasValue && e.FechaRetiro.Value <= hasta.Value))
                .ToArray());
    }

    // =========================================================================
    // PRUEBA 1: Registrar con datos válidos
    // =========================================================================
    [Fact]
    public async Task RegistrarAsync_EmailYNumeroEmpleadoDisponibles_RegistraEmpleado()
    {
        var repo = new FakeRepository();
        var service = CrearServicio(repo);
        var empleado = new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15));

        var resultado = await service.RegistrarAsync(empleado);

        Assert.NotNull(resultado);
        Assert.Equal("E001", resultado.Id);
        Assert.Equal(EstadoEmpleado.Activo, resultado.Estado);
    }

    // =========================================================================
    // PRUEBA 2: Email duplicado lanza excepción
    // =========================================================================
    [Fact]
    public async Task RegistrarAsync_EmailDuplicado_LanzaEmpleadoDuplicadoException()
    {
        var repo = new FakeRepository();
        var service = CrearServicio(repo);

        await service.RegistrarAsync(new Empleado(
            "E001", "Ana", "López", "ana@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        var ex = await Assert.ThrowsAsync<EmpleadoDuplicadoException>(() =>
            service.RegistrarAsync(new Empleado(
                "E002", "Beto", "Gómez", "ana@test.com", "EMP002", "Dev", "Tech", "IT",
                new DateOnly(2024, 1, 16))));

        Assert.Equal("email", ex.Campo);
        Assert.Equal("ana@test.com", ex.Valor);
    }

    // =========================================================================
    // PRUEBA 3: Número de empleado duplicado lanza excepción
    // =========================================================================
    [Fact]
    public async Task RegistrarAsync_NumeroEmpleadoDuplicado_LanzaEmpleadoDuplicadoException()
    {
        var repo = new FakeRepository();
        var service = CrearServicio(repo);

        await service.RegistrarAsync(new Empleado(
            "E001", "Ana", "López", "ana@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        var ex = await Assert.ThrowsAsync<EmpleadoDuplicadoException>(() =>
            service.RegistrarAsync(new Empleado(
                "E002", "Beto", "Gómez", "beto@test.com", "EMP001", "QA", "Tech", "IT",
                new DateOnly(2024, 1, 16))));

        Assert.Equal("numeroEmpleado", ex.Campo);
        Assert.Equal("EMP001", ex.Valor);
    }

    [Fact]
    public async Task RetirarAsync_EmpleadoActivo_CambiaEstadoGuardaFechaYPublicaEvento()
    {
        var repo = new FakeRepository();
        var empleado = NuevoEmpleado();
        await repo.RegistrarAsync(empleado);
        var publisher = new RecordingEventPublisher();
        var service = CrearServicio(repo, eventPublisher: publisher);
        var inicio = DateTime.UtcNow;

        var resultado = await service.RetirarAsync(empleado.Id);

        var fin = DateTime.UtcNow;
        Assert.NotNull(resultado);
        Assert.Equal(EstadoEmpleado.Retirado, resultado.Estado);
        Assert.NotNull(resultado.FechaRetiro);
        Assert.InRange(resultado.FechaRetiro.Value, inicio, fin);
        Assert.Equal(1, repo.Actualizaciones);
        Assert.Same(resultado, await service.BuscarPorIdAsync(empleado.Id));
        Assert.Equal("empleado.retirado", publisher.EventType);
        Assert.Same(resultado, publisher.Data);
    }

    [Fact]
    public async Task RetirarAsync_EmpleadoYaRetirado_RechazaSinCambiarFechaNiPublicarOtroEvento()
    {
        var repo = new FakeRepository();
        var empleado = NuevoEmpleado();
        await repo.RegistrarAsync(empleado);
        var publisher = new RecordingEventPublisher();
        var service = CrearServicio(repo, eventPublisher: publisher);
        await service.RetirarAsync(empleado.Id);
        var fechaRetiroOriginal = empleado.FechaRetiro;

        var exception = await Assert.ThrowsAsync<EmpleadoYaRetiradoException>(
            () => service.RetirarAsync(empleado.Id));

        Assert.Equal($"El empleado con id {empleado.Id} ya está retirado.", exception.Message);
        Assert.Equal(fechaRetiroOriginal, empleado.FechaRetiro);
        Assert.Equal(1, repo.Actualizaciones);
        Assert.Equal(1, publisher.PublishCount);
    }

    // =========================================================================
    // PRUEBA 4: Buscar empleado existente
    // =========================================================================
    [Fact]
    public async Task BuscarPorIdAsync_Existente_RetornaEmpleado()
    {
        var repo = new FakeRepository();
        var service = CrearServicio(repo);

        await service.RegistrarAsync(new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        var resultado = await service.BuscarPorIdAsync("E001");

        Assert.NotNull(resultado);
        Assert.Equal("Juan", resultado!.Nombre);
    }

    // =========================================================================
    // PRUEBA 5: Buscar empleado inexistente
    // =========================================================================
    [Fact]
    public async Task BuscarPorIdAsync_Inexistente_RetornaNull()
    {
        var repo = new FakeRepository();
        var service = CrearServicio(repo);

        var resultado = await service.BuscarPorIdAsync("NO-EXISTE");

        Assert.Null(resultado);
    }

    // =========================================================================
    // PRUEBA 6: Buscar con ID vacío lanza ArgumentException
    // =========================================================================
    [Fact]
    public async Task BuscarPorIdAsync_IdVacio_LanzaArgumentException()
    {
        var repo = new FakeRepository();
        var service = CrearServicio(repo);

        await Assert.ThrowsAsync<ArgumentException>(() => service.BuscarPorIdAsync(""));
    }

    // =========================================================================
    // PRUEBAS DEL FALLBACK (Reto 3): departamentos no disponible -> PENDIENTE_VALIDACION
    // =========================================================================
    [Fact]
    public async Task RegistrarAsync_DepartamentosNoDisponible_RegistraComoPendienteValidacion()
    {
        var repo = new FakeRepository();
        var service = CrearServicio(repo, new FakeDepartamentoClient(
            () => throw new DepartamentosNoDisponibleException("departamentos caído")));

        var resultado = await service.RegistrarAsync(NuevoEmpleado());

        Assert.Equal(EstadoEmpleado.PendienteValidacion, resultado.Estado);
        var guardado = await service.BuscarPorIdAsync("E001");
        Assert.NotNull(guardado);
        Assert.Equal(EstadoEmpleado.PendienteValidacion, guardado!.Estado);
    }

    [Fact]
    public async Task RegistrarAsync_CircuitoAbierto_RegistraComoPendienteValidacion()
    {
        var service = CrearServicio(new FakeRepository(), new FakeDepartamentoClient(
            () => throw new DepartamentosCircuitoAbiertoException("circuito abierto")));

        var resultado = await service.RegistrarAsync(NuevoEmpleado());

        Assert.Equal(EstadoEmpleado.PendienteValidacion, resultado.Estado);
    }

    [Fact]
    public async Task RegistrarAsync_DepartamentoInexistente_NoActivaElFallback()
    {
        var repo = new FakeRepository();
        var service = CrearServicio(repo, new FakeDepartamentoClient(() => false));

        await Assert.ThrowsAsync<DepartamentoNoEncontradoException>(
            () => service.RegistrarAsync(NuevoEmpleado()));

        Assert.Null(await service.BuscarPorIdAsync("E001")); // no se guardó nada
    }

    [Fact]
    public async Task RegistrarAsync_ConFallback_SigueValidandoDuplicados()
    {
        var service = CrearServicio(new FakeRepository(), new FakeDepartamentoClient(
            () => throw new DepartamentosNoDisponibleException("departamentos caído")));
        await service.RegistrarAsync(NuevoEmpleado());

        await Assert.ThrowsAsync<EmpleadoDuplicadoException>(
            () => service.RegistrarAsync(NuevoEmpleado(id: "E002")));
    }

    private static Empleado NuevoEmpleado(string id = "E001")
        => new(id, "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15));

    private static EmpleadoDomainService CrearServicio(
        IEmpleadoRepository repository,
        IDepartamentoClient? departamentoClient = null,
        IEventPublisher? eventPublisher = null)
        => new(
            repository,
            departamentoClient ?? new FakeDepartamentoClient(),
            eventPublisher ?? new NoOpEventPublisher());

    private sealed class RecordingEventPublisher : IEventPublisher
    {
        public string? EventType { get; private set; }

        public object? Data { get; private set; }

        public int PublishCount { get; private set; }

        public void Publish<T>(string eventType, T data)
        {
            PublishCount++;
            EventType = eventType;
            Data = data;
        }
    }

    private sealed class NoOpEventPublisher : IEventPublisher
    {
        public void Publish<T>(string eventType, T data) { }
    }

    private sealed class FakeDepartamentoClient(Func<bool>? respuesta = null) : IDepartamentoClient
    {
        public Task<bool> ExisteAsync(
            string departamentoId,
            CancellationToken cancellationToken = default)
            => Task.FromResult(respuesta?.Invoke() ?? true);
    }
}
