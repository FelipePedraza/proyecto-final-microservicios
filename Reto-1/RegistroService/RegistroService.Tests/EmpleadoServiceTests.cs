using RegistroService.Domain.Entities;
using RegistroService.Domain.Exceptions;
using RegistroService.Domain.Repositories;
using RegistroService.Domain.Services;
using Xunit;

namespace RegistroService.Tests;

public sealed class EmpleadoServiceTests
{
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

        public Task<bool> ExisteEmailAsync(string email, CancellationToken ct = default)
            => Task.FromResult(_emails.Contains(email));

        public Task<bool> ExisteNumeroEmpleadoAsync(string numero, CancellationToken ct = default)
            => Task.FromResult(_numeros.Contains(numero));

        public Task RegistrarAsync(Empleado empleado, CancellationToken ct = default)
        {
            _empleados[empleado.Id] = empleado;
            _emails.Add(empleado.Email);
            _numeros.Add(empleado.NumeroEmpleado);
            return Task.CompletedTask;
        }
    }

    [Fact]
    public async Task RegistrarAsync_ConDatosValidos_RegistraEmpleado()
    {
        var repo = new FakeRepository();
        var service = new EmpleadoService(repo);
        var empleado = new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15));

        var resultado = await service.RegistrarAsync(empleado);

        Assert.NotNull(resultado);
        Assert.Equal("E001", resultado.Id);
        Assert.Equal(EstadoEmpleado.Activo, resultado.Estado);
    }

    [Fact]
    public async Task RegistrarAsync_EmailDuplicado_LanzaEmpleadoDuplicadoException()
    {
        var repo = new FakeRepository();
        var service = new EmpleadoService(repo);

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

    [Fact]
    public async Task RegistrarAsync_NumeroEmpleadoDuplicado_LanzaEmpleadoDuplicadoException()
    {
        var repo = new FakeRepository();
        var service = new EmpleadoService(repo);

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
    public async Task BuscarPorIdAsync_Existente_RetornaEmpleado()
    {
        var repo = new FakeRepository();
        var service = new EmpleadoService(repo);

        await service.RegistrarAsync(new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        var resultado = await service.BuscarPorIdAsync("E001");

        Assert.NotNull(resultado);
        Assert.Equal("Juan", resultado!.Nombre);
    }

    [Fact]
    public async Task BuscarPorIdAsync_Inexistente_RetornaNull()
    {
        var repo = new FakeRepository();
        var service = new EmpleadoService(repo);

        var resultado = await service.BuscarPorIdAsync("NO-EXISTE");

        Assert.Null(resultado);
    }

    [Fact]
    public async Task BuscarPorIdAsync_IdVacio_LanzaArgumentException()
    {
        var repo = new FakeRepository();
        var service = new EmpleadoService(repo);

        await Assert.ThrowsAsync<ArgumentException>(() => service.BuscarPorIdAsync(""));
    }
}
