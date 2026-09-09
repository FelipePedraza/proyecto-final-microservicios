// =============================================================================
// EmpleadoRepositoryTests.cs
// Pruebas unitarias del repositorio de empleados (capa de persistencia).
// Objetivo: verificar que el repositorio almacene, busque y detecte duplicados
//           correctamente, incluyendo concurrencia.
// Estrategia: usamos la implementación real (EmpleadoRepository) porque
//             queremos probar el comportamiento thread-safe con lock.
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

    // =========================================================================
    // PRUEBA 6: Concurrencia — 10 solicitudes simultáneas con mismo email
    // =========================================================================
    // Caso: 10 tareas en paralelo intentan registrar con el MISMO email.
    // Resultado esperado: solo 1 registro tiene éxito; los otros 9 fallan.
    // Verificamos contando cuántos empleados con ese email existen al final.
    [Fact]
    public async Task RegistrarAsync_Concurrentemente_UnSoloRegistroExitoso()
    {
        var repo = CrearRepositorio();
        const string email = "concurrente@test.com";

        var tareas = Enumerable.Range(1, 10)
            .Select(i => repo.RegistrarAsync(new Empleado(
                $"E{i:000}", $"Nom{i}", $"Ape{i}", email, $"EMP{i:000}", "Dev", "Tech", "IT",
                new DateOnly(2024, 1, 15))));

        // Ejecutamos todas en paralelo. Algunas lanzarán EmpleadoDuplicadoException.
        // Capturamos el AggregateException para que el test no falle aquí.
        try
        {
            await Task.WhenAll(tareas);
        }
        catch
        {
            // Esperado: varias tareas fallaron por email duplicado dentro del lock
        }

        // Contamos cuántos empleados con ese email se registraron realmente
        int contador = 0;
        for (int i = 1; i <= 10; i++)
        {
            var e = await repo.ObtenerPorIdAsync($"E{i:000}");
            if (e != null) contador++;
        }

        // Solo 1 de los 10 intentos debió tener éxito
        Assert.Equal(1, contador);
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
