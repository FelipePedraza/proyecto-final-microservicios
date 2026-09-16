// =============================================================================
// EmpleadoRepositoryTests.cs
// Pruebas unitarias del repositorio de empleados (capa de persistencia).
// Objetivo: verificar que el repositorio almacene, busque y detecte duplicados
//           correctamente.
// Estrategia: usamos la implementación real (EmpleadoRepository).
// =============================================================================
using RegistroService.Domain.Entities;
using RegistroService.Domain.Exceptions;
using RegistroService.Domain.Repositories;
using RegistroService.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace RegistroService.Tests;

/// <summary>
/// Pruebas unitarias de EmpleadoRepository.
/// Cada [Fact] es un caso de prueba independiente.
/// </summary>
public sealed class EmpleadoRepositoryTests
{
    // =========================================================================
    // PRUEBA 1: Registrar empleado nuevo
    // =========================================================================
    [Fact]
    public async Task RegistrarAsync_EmpleadoNuevo_AgregaCorrectamente()
    {
        var repo = CrearRepositorio();
        var empleado = new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15));

        await repo.RegistrarAsync(empleado);

        var encontrado = await repo.ObtenerPorIdAsync("E001");
        Assert.NotNull(encontrado);
        Assert.Equal("Juan", encontrado!.Nombre);
    }

    // =========================================================================
    // PRUEBA 2: ID duplicado lanza excepción
    // =========================================================================
    [Fact]
    public async Task RegistrarAsync_IdDuplicado_LanzaEmpleadoDuplicadoException()
    {
        var repo = CrearRepositorio();
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

    // =========================================================================
    // PRUEBA 3: Email duplicado (case-insensitive) lanza excepción
    // =========================================================================
    [Fact]
    public async Task RegistrarAsync_EmailDuplicado_LanzaEmpleadoDuplicadoException()
    {
        var repo = CrearRepositorio();
        await repo.RegistrarAsync(new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        var ex = await Assert.ThrowsAsync<EmpleadoDuplicadoException>(() =>
            repo.RegistrarAsync(new Empleado(
                "E002", "Ana", "López", "JUAN@TEST.COM", "EMP002", "QA", "Tech", "IT",
                new DateOnly(2024, 1, 16))));

        Assert.Equal("email", ex.Campo);
    }

    // =========================================================================
    // PRUEBA 4: Número de empleado duplicado lanza excepción
    // =========================================================================
    [Fact]
    public async Task RegistrarAsync_NumeroEmpleadoDuplicado_LanzaEmpleadoDuplicadoException()
    {
        var repo = CrearRepositorio();
        await repo.RegistrarAsync(new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        var ex = await Assert.ThrowsAsync<EmpleadoDuplicadoException>(() =>
            repo.RegistrarAsync(new Empleado(
                "E002", "Ana", "López", "ana@test.com", "EMP001", "QA", "Tech", "IT",
                new DateOnly(2024, 1, 16))));

        Assert.Equal("numeroEmpleado", ex.Campo);
    }

    // =========================================================================
    // PRUEBA 5: Buscar empleado inexistente
    // =========================================================================
    [Fact]
    public async Task ObtenerPorIdAsync_NoExistente_RetornaNull()
    {
        var repo = CrearRepositorio();

        var resultado = await repo.ObtenerPorIdAsync("NO-EXISTE");

        Assert.Null(resultado);
    }

    private static EmpleadoRepository CrearRepositorio()
    {
        var options = new DbContextOptionsBuilder<RegistroDbContext>()
            .UseInMemoryDatabase(Guid.NewGuid().ToString())
            .Options;
        var context = new RegistroDbContext(options);
        context.Database.EnsureCreated();
        return new EmpleadoRepository(context);
    }
}
