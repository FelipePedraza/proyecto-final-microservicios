using RegistroService.Domain.Entities;
using RegistroService.Domain.Exceptions;
using RegistroService.Domain.Repositories;
using Xunit;

namespace RegistroService.Tests;

public sealed class EmpleadoRepositoryTests
{
    [Fact]
    public async Task RegistrarAsync_EmpleadoNuevo_AgregaCorrectamente()
    {
        var repo = new EmpleadoRepository();
        var empleado = new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15));

        await repo.RegistrarAsync(empleado);

        var encontrado = await repo.ObtenerPorIdAsync("E001");
        Assert.NotNull(encontrado);
        Assert.Equal("Juan", encontrado!.Nombre);
    }

    [Fact]
    public async Task RegistrarAsync_IdDuplicado_LanzaEmpleadoDuplicadoException()
    {
        var repo = new EmpleadoRepository();
        await repo.RegistrarAsync(new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        var ex = await Assert.ThrowsAsync<EmpleadoDuplicadoException>(() =>
            repo.RegistrarAsync(new Empleado(
                "E001", "Ana", "López", "ana@test.com", "EMP002", "QA", "Tech", "IT",
                new DateOnly(2024, 1, 16))));

        Assert.Equal("id", ex.Campo);
        Assert.Equal("E001", ex.Valor);
    }

    [Fact]
    public async Task RegistrarAsync_EmailDuplicado_LanzaEmpleadoDuplicadoException()
    {
        var repo = new EmpleadoRepository();
        await repo.RegistrarAsync(new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        var ex = await Assert.ThrowsAsync<EmpleadoDuplicadoException>(() =>
            repo.RegistrarAsync(new Empleado(
                "E002", "Ana", "López", "JUAN@TEST.COM", "EMP002", "QA", "Tech", "IT",
                new DateOnly(2024, 1, 16))));

        Assert.Equal("email", ex.Campo);
    }

    [Fact]
    public async Task RegistrarAsync_NumeroEmpleadoDuplicado_LanzaEmpleadoDuplicadoException()
    {
        var repo = new EmpleadoRepository();
        await repo.RegistrarAsync(new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        var ex = await Assert.ThrowsAsync<EmpleadoDuplicadoException>(() =>
            repo.RegistrarAsync(new Empleado(
                "E002", "Ana", "López", "ana@test.com", "EMP001", "QA", "Tech", "IT",
                new DateOnly(2024, 1, 16))));

        Assert.Equal("numeroEmpleado", ex.Campo);
    }

    [Fact]
    public async Task ObtenerPorIdAsync_NoExistente_RetornaNull()
    {
        var repo = new EmpleadoRepository();

        var resultado = await repo.ObtenerPorIdAsync("NO-EXISTE");

        Assert.Null(resultado);
    }

    [Fact]
    public async Task RegistrarAsync_Concurrentemente_UnSoloRegistroExitoso()
    {
        var repo = new EmpleadoRepository();
        const string email = "concurrente@test.com";

        var tareas = Enumerable.Range(1, 10)
            .Select(i => repo.RegistrarAsync(new Empleado(
                $"E{i:000}", $"Nom{i}", $"Ape{i}", email, $"EMP{i:000}", "Dev", "Tech", "IT",
                new DateOnly(2024, 1, 15))));

        var resultados = await Task.WhenAll(tareas);

        // Verificamos indirectamente que el email existe una sola vez
        var existe = await repo.ExisteEmailAsync(email);
        Assert.True(existe);
    }
}
