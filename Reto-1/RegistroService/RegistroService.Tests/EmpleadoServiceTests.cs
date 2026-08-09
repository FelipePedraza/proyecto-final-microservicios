// =============================================================================
// EmpleadoServiceTests.cs
// Pruebas unitarias del servicio de empleados (capa de dominio).
// Objetivo: verificar la lógica de negocio sin depender de HTTP ni de la API.
// Estrategia: usamos un FakeRepository que implementa IEmpleadoRepository
//             pero almacena datos en memoria sin locks, para aislar el servicio.
// =============================================================================
using RegistroService.Domain.Entities;
using RegistroService.Domain.Exceptions;
using RegistroService.Domain.Repositories;
using RegistroService.Domain.Services;
using Xunit;

namespace RegistroService.Tests;

/// <summary>
/// Pruebas unitarias de EmpleadoService.
/// Cada [Fact] es un caso de prueba independiente que el profesor puede ver
/// como una "historia de comportamiento" del servicio.
/// </summary>
public sealed class EmpleadoServiceTests
{
    // -------------------------------------------------------------------------
    // FAKE REPOSITORY
    // -------------------------------------------------------------------------
    // Un FakeRepository es un doble de prueba que implementa la interfaz
    // IEmpleadoRepository pero con diccionarios en memoria simples.
    // Ventaja: no necesita lock, no tiene dependencias externas y permite
    // verificar qué se guardó sin acceder a la implementación real.
    // -------------------------------------------------------------------------
    private sealed class FakeRepository : IEmpleadoRepository
    {
        // Diccionario principal: id -> Empleado (simula la tabla de empleados)
        private readonly Dictionary<string, Empleado> _empleados = new();
        // HashSet de emails: case-insensitive (OrdinalIgnoreCase)
        // Esto reproduce el comportamiento real del repositorio:
        // se almacena en minúsculas y la búsqueda es case-insensitive.
        private readonly HashSet<string> _emails = new(StringComparer.OrdinalIgnoreCase);
        // HashSet de numerosEmpleado: case-sensitive (Ordinal)
        private readonly HashSet<string> _numeros = new(StringComparer.Ordinal);

        // ObtenerPorIdAsync: busca en el diccionario por clave exacta.
        // Retorna Task.FromResult para mantener la firma async del contrato.
        public Task<Empleado?> ObtenerPorIdAsync(string id, CancellationToken ct = default)
        {
            _empleados.TryGetValue(id, out var e);
            return Task.FromResult(e);
        }

        // ExisteEmailAsync: busca en el HashSet de emails.
        // El StringComparer.OrdinalIgnoreCase hace que "juan@test.com" y
        // "JUAN@TEST.COM" sean considerados el mismo email.
        public Task<bool> ExisteEmailAsync(string email, CancellationToken ct = default)
            => Task.FromResult(_emails.Contains(email));

        // ExisteNumeroEmpleadoAsync: busca en el HashSet de numeros.
        // StringComparer.Ordinal: sensible a mayúsculas/minúsculas.
        public Task<bool> ExisteNumeroEmpleadoAsync(string numero, CancellationToken ct = default)
            => Task.FromResult(_numeros.Contains(numero));

        // RegistrarAsync: guarda en las tres estructuras simultáneamente.
        // Nota: no tiene lock porque el FakeRepository se usa desde un solo
        // hilo en pruebas unitarias. El lock real está en EmpleadoRepository.
        public Task RegistrarAsync(Empleado empleado, CancellationToken ct = default)
        {
            _empleados[empleado.Id] = empleado;
            _emails.Add(empleado.Email);
            _numeros.Add(empleado.NumeroEmpleado);
            return Task.CompletedTask;
        }
    }

    // =========================================================================
    // PRUEBA 1: Registrar con datos válidos
    // =========================================================================
    // Caso: el empleado tiene todos los campos correctos, no existe previamente.
    // Resultado esperado: el servicio retorna el empleado registrado con
    // Estado = Activo. Verificamos que el ID se conserve y el estado sea correcto.
    [Fact]
    public async Task RegistrarAsync_ConDatosValidos_RegistraEmpleado()
    {
        // Arrange: preparamos el entorno de prueba
        var repo = new FakeRepository();                        // Repositorio simulado
        var service = new EmpleadoService(repo);                // Servicio bajo prueba
        var empleado = new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15));                         // Entidad de dominio válida

        // Act: ejecutamos el método que queremos probar
        var resultado = await service.RegistrarAsync(empleado);

        // Assert: verificamos el resultado
        Assert.NotNull(resultado);                              // No debe ser null
        Assert.Equal("E001", resultado.Id);                     // El ID se conserva
        Assert.Equal(EstadoEmpleado.Activo, resultado.Estado);  // Estado por defecto es Activo
    }

    // =========================================================================
    // PRUEBA 2: Email duplicado lanza excepción
    // =========================================================================
    // Caso: ya existe un empleado con email "ana@test.com".
    //        Intentamos registrar otro con el mismo email.
    // Resultado esperado: EmpleadoDuplicadoException con Campo = "email"
    //                     y Valor = "ana@test.com".
    [Fact]
    public async Task RegistrarAsync_EmailDuplicado_LanzaEmpleadoDuplicadoException()
    {
        // Arrange: creamos el servicio y registramos un empleado previo
        var repo = new FakeRepository();
        var service = new EmpleadoService(repo);

        // Primer registro: este empleado SÍ existe en el "sistema"
        await service.RegistrarAsync(new Empleado(
            "E001", "Ana", "López", "ana@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        // Act + Assert: intentamos registrar otro con el MISMO email
        // Assert.ThrowsAsync captura la excepción y la asigna a 'ex'
        var ex = await Assert.ThrowsAsync<EmpleadoDuplicadoException>(() =>
            service.RegistrarAsync(new Empleado(
                "E002", "Beto", "Gómez", "ana@test.com", "EMP002", "Dev", "Tech", "IT",
                new DateOnly(2024, 1, 16))));   // mismo email, diferente ID

        // Verificamos que la excepción tenga la información correcta
        Assert.Equal("email", ex.Campo);                // El campo conflictivo es "email"
        Assert.Equal("ana@test.com", ex.Valor);         // El valor duplicado es ese email
    }

    // =========================================================================
    // PRUEBA 3: Número de empleado duplicado lanza excepción
    // =========================================================================
    // Caso: ya existe un empleado con numeroEmpleado "EMP001".
    //        Intentamos registrar otro con el mismo numeroEmpleado.
    // Resultado esperado: EmpleadoDuplicadoException con Campo = "numeroEmpleado"
    //                     y Valor = "EMP001".
    [Fact]
    public async Task RegistrarAsync_NumeroEmpleadoDuplicado_LanzaEmpleadoDuplicadoException()
    {
        // Arrange
        var repo = new FakeRepository();
        var service = new EmpleadoService(repo);

        // Primer registro con numeroEmpleado = "EMP001"
        await service.RegistrarAsync(new Empleado(
            "E001", "Ana", "López", "ana@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        // Act + Assert: intentamos registrar otro con el MISMO numeroEmpleado
        var ex = await Assert.ThrowsAsync<EmpleadoDuplicadoException>(() =>
            service.RegistrarAsync(new Empleado(
                "E002", "Beto", "Gómez", "beto@test.com", "EMP001", "QA", "Tech", "IT",
                new DateOnly(2024, 1, 16))));   // diferente email, mismo numeroEmpleado

        // Verificamos la excepción
        Assert.Equal("numeroEmpleado", ex.Campo);   // Campo conflictivo
        Assert.Equal("EMP001", ex.Valor);           // Valor duplicado
    }

    // =========================================================================
    // PRUEBA 4: Buscar empleado existente
    // =========================================================================
    // Caso: registramos un empleado y luego lo buscamos por su ID.
    // Resultado esperado: el servicio retorna el empleado encontrado.
    [Fact]
    public async Task BuscarPorIdAsync_Existente_RetornaEmpleado()
    {
        // Arrange: servicio con un empleado ya registrado
        var repo = new FakeRepository();
        var service = new EmpleadoService(repo);

        await service.RegistrarAsync(new Empleado(
            "E001", "Juan", "Pérez", "juan@test.com", "EMP001", "Dev", "Tech", "IT",
            new DateOnly(2024, 1, 15)));

        // Act: buscamos el empleado que acabamos de registrar
        var resultado = await service.BuscarPorIdAsync("E001");

        // Assert: el resultado no es null y tiene el nombre correcto
        Assert.NotNull(resultado);           // Debe encontrarlo
        Assert.Equal("Juan", resultado!.Nombre); // Nombre debe coincidir
    }

    // =========================================================================
    // PRUEBA 5: Buscar empleado inexistente
    // =========================================================================
    // Caso: buscamos un ID que nunca se registró.
    // Resultado esperado: null (el servicio NO lanza excepción, retorna null).
    [Fact]
    public async Task BuscarPorIdAsync_Inexistente_RetornaNull()
    {
        // Arrange: servicio vacío (sin empleados registrados)
        var repo = new FakeRepository();
        var service = new EmpleadoService(repo);

        // Act: buscamos un ID que no existe
        var resultado = await service.BuscarPorIdAsync("NO-EXISTE");

        // Assert: debe retornar null, no una excepción
        Assert.Null(resultado);
    }

    // =========================================================================
    // PRUEBA 6: Buscar con ID vacío lanza ArgumentException
    // =========================================================================
    // Caso: el método BuscarPorIdAsync recibe un string vacío.
    //        El servicio tiene validación: ArgumentException.ThrowIfNullOrWhiteSpace(id)
    // Resultado esperado: ArgumentException (defensa contra parámetros inválidos).
    [Fact]
    public async Task BuscarPorIdAsync_IdVacio_LanzaArgumentException()
    {
        // Arrange
        var repo = new FakeRepository();
        var service = new EmpleadoService(repo);

        // Act + Assert: pasar cadena vacía debe lanzar ArgumentException
        await Assert.ThrowsAsync<ArgumentException>(() => service.BuscarPorIdAsync(""));
    }
}
