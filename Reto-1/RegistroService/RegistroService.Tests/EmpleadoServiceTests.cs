// =============================================================================
// EmpleadoServiceTests.cs
// Pruebas unitarias del servicio de empleados (capa de dominio).
// Objetivo: verificar la lógica de negocio sin depender de HTTP ni de la API.
// Estrategia: usamos un FakeRepository que implementa IEmpleadoRepository
//             con validaciones de duplicados para simular el comportamiento real.
// =============================================================================
using RegistroService.Domain.Entities;
using RegistroService.Domain.Enums;
using RegistroService.Domain.Exceptions;
using RegistroService.Domain.Repositories;
using RegistroService.Domain.Services;
using RegistroService.Infrastructure.Departamentos;
using Xunit;

namespace RegistroService.Tests;

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
    }

    // =========================================================================
    // PRUEBA 1: Registrar con datos válidos
    // =========================================================================
    [Fact]
    public async Task RegistrarAsync_ConDatosValidos_RegistraEmpleado()
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

    private static EmpleadoService CrearServicio(IEmpleadoRepository repository)
        => new(repository, new FakeDepartamentoClient());

    private sealed class FakeDepartamentoClient : IDepartamentoClient
    {
        public Task<bool> ExisteAsync(
            string departamentoId,
            CancellationToken cancellationToken = default)
            => Task.FromResult(true);
    }
}
