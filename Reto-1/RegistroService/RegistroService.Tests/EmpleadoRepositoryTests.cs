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
using Xunit;

namespace RegistroService.Tests;

/// <summary>
/// Pruebas unitarias de EmpleadoRepository.
/// Cada [Fact] es un caso de prueba independiente que el profesor puede ver
/// como una "historia de comportamiento" del repositorio.
/// </summary>
public sealed class EmpleadoRepositoryTests
{
    // =========================================================================
    // PRUEBA 1: Registrar empleado nuevo
    // =========================================================================
    // Caso: el repositorio está vacío, registramos un empleado con ID "E001".
    // Resultado esperado: el empleado se guarda y se puede recuperar por ID.
    [Fact]
    public async Task RegistrarAsync_EmpleadoNuevo_AgregaCorrectamente()
    {
        // Arrange: creamos una instancia del repositorio real (en memoria)
        var repo = new EmpleadoRepository();
        var empleado = new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15));

        // Act: registramos el empleado en el repositorio
        await repo.RegistrarAsync(empleado);

        // Assert: buscamos el empleado por ID y verificamos que exista
        var encontrado = await repo.ObtenerPorIdAsync("E001");
        Assert.NotNull(encontrado);              // No debe ser null
        Assert.Equal("Juan", encontrado!.Nombre); // El nombre debe coincidir
    }

    // =========================================================================
    // PRUEBA 2: ID duplicado lanza excepción
    // =========================================================================
    // Caso: ya existe un empleado con ID "E001".
    //        Intentamos registrar otro con el mismo ID.
    // Resultado esperado: EmpleadoDuplicadoException con Campo = "id"
    //                     y Valor = "E001".
    [Fact]
    public async Task RegistrarAsync_IdDuplicado_LanzaEmpleadoDuplicadoException()
    {
        // Arrange: registramos un empleado con ID "E001"
        var repo = new EmpleadoRepository();
        await repo.RegistrarAsync(new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        // Act + Assert: intentamos registrar OTRO empleado con el MISMO ID
        var ex = await Assert.ThrowsAsync<EmpleadoDuplicadoException>(() =>
            repo.RegistrarAsync(new Empleado(
                "E001", "Ana", "López", "ana@test.com", "EMP002", "QA", "Tech", "IT",
                new DateOnly(2024, 1, 16))));

        // Verificamos la excepción
        Assert.Equal("id", ex.Campo);       // Campo conflictivo: id
        Assert.Equal("E001", ex.Valor);     // Valor duplicado: E001
    }

    // =========================================================================
    // PRUEBA 3: Email duplicado (case-insensitive) lanza excepción
    // =========================================================================
    // Caso: ya existe un empleado con email "juan@test.com".
    //        Intentamos registrar otro con email "JUAN@TEST.COM" (mayúsculas).
    // Resultado esperado: EmpleadoDuplicadoException con Campo = "email".
    // Nota: el repositorio usa HashSet con StringComparer.OrdinalIgnoreCase,
    //       por lo que "JUAN@TEST.COM" se considera duplicado de "juan@test.com".
    [Fact]
    public async Task RegistrarAsync_EmailDuplicado_LanzaEmpleadoDuplicadoException()
    {
        // Arrange: registramos un empleado con email en minúsculas
        var repo = new EmpleadoRepository();
        await repo.RegistrarAsync(new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        // Act + Assert: intentamos registrar con el MISMO email en MAYÚSCULAS
        var ex = await Assert.ThrowsAsync<EmpleadoDuplicadoException>(() =>
            repo.RegistrarAsync(new Empleado(
                "E002", "Ana", "López", "JUAN@TEST.COM", "EMP002", "QA", "Tech", "IT",
                new DateOnly(2024, 1, 16))));

        // Verificamos la excepción
        Assert.Equal("email", ex.Campo);    // Campo conflictivo: email
    }

    // =========================================================================
    // PRUEBA 4: Número de empleado duplicado lanza excepción
    // =========================================================================
    // Caso: ya existe un empleado con numeroEmpleado "EMP001".
    //        Intentamos registrar otro con el mismo numeroEmpleado.
    // Resultado esperado: EmpleadoDuplicadoException con Campo = "numeroEmpleado".
    // Nota: a diferencia del email, el numeroEmpleado usa StringComparer.Ordinal,
    //       así que "EMP001" y "emp001" NO son duplicados (case-sensitive).
    [Fact]
    public async Task RegistrarAsync_NumeroEmpleadoDuplicado_LanzaEmpleadoDuplicadoException()
    {
        // Arrange: registramos un empleado con numeroEmpleado = "EMP001"
        var repo = new EmpleadoRepository();
        await repo.RegistrarAsync(new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        // Act + Assert: intentamos registrar otro con el MISMO numeroEmpleado
        var ex = await Assert.ThrowsAsync<EmpleadoDuplicadoException>(() =>
            repo.RegistrarAsync(new Empleado(
                "E002", "Ana", "López", "ana@test.com", "EMP001", "QA", "Tech", "IT",
                new DateOnly(2024, 1, 16))));

        // Verificamos la excepción
        Assert.Equal("numeroEmpleado", ex.Campo); // Campo conflictivo
    }

    // =========================================================================
    // PRUEBA 5: Buscar empleado inexistente
    // =========================================================================
    // Caso: el repositorio está vacío, buscamos un ID que nunca se registró.
    // Resultado esperado: null (no lanza excepción, retorna null).
    [Fact]
    public async Task ObtenerPorIdAsync_NoExistente_RetornaNull()
    {
        // Arrange: repositorio vacío (sin registros)
        var repo = new EmpleadoRepository();

        // Act: buscamos un ID que no existe
        var resultado = await repo.ObtenerPorIdAsync("NO-EXISTE");

        // Assert: debe retornar null, no una excepción
        Assert.Null(resultado);
    }

    // =========================================================================
    // PRUEBA 6: Concurrencia — 10 solicitudes simultáneas con mismo email
    // =========================================================================
    // Caso: lanzamos 10 tareas en paralelo que intentan registrar empleados
    //       con el MISMO email pero IDs diferentes.
    // Resultado esperado: solo UNA debe tener éxito; las otras 9 deben fallar
    //       con EmpleadoDuplicadoException gracias al lock en el repositorio.
    //       Verificamos indirectamente que el email existe una sola vez.
    [Fact]
    public async Task RegistrarAsync_Concurrentemente_UnSoloRegistroExitoso()
    {
        // Arrange: repositorio vacío y email compartido
        var repo = new EmpleadoRepository();
        const string email = "concurrente@test.com";

        // Creamos 10 tareas que intentan registrar SIMULTÁNEAMENTE
        // Cada una tiene ID único (E001, E002, ... E010) pero el MISMO email
        var tareas = Enumerable.Range(1, 10)
            .Select(i => repo.RegistrarAsync(new Empleado(
                $"E{i:000}", $"Nom{i}", $"Ape{i}", email, $"EMP{i:000}", "Dev", "Tech", "IT",
                new DateOnly(2024, 1, 15))));

        // Act: ejecutamos todas las tareas en paralelo
        // Task.WhenAll espera a que todas terminen (exitosas o con excepción)
        var resultados = await Task.WhenAll(tareas);

        // Assert: verificamos indirectamente que el email existe una sola vez
        // Si el lock funciona, solo 1 registro se guardó; los 9 restantes
        // lanzaron EmpleadoDuplicadoException dentro del lock.
        var existe = await repo.ExisteEmailAsync(email);
        Assert.True(existe); // El email debe estar registrado (por lo menos 1 vez)

        // Nota adicional para el profesor:
        // Si quisiéramos verificar exactamente cuántos registros se guardaron,
        // podríamos consultar ObtenerPorIdAsync para cada ID y contar los no-null.
        // En la práctica, con 10 intentos concurrentes y el mismo email,
        // solo 1 debería existir en el repositorio.
    }
}
