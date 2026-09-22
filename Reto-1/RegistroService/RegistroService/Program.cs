using RegistroService.Domain.Repositories;
using RegistroService.Domain.Services;
using RegistroService.Domain.Exceptions;
using RegistroService.API.DTOs;
using RegistroService.API.Extensions;
using Microsoft.AspNetCore.Diagnostics;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using RegistroService.Infrastructure.Departamentos;
using RegistroService.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore.Storage;
using Npgsql;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddDbContext<RegistroDbContext>(options =>
    options.UseNpgsql(
        builder.Configuration.GetConnectionString("Registro"),
        npgsql => npgsql.EnableRetryOnFailure(
            maxRetryCount: 3,
            maxRetryDelay: TimeSpan.FromSeconds(2),
            errorCodesToAdd: null)));
builder.Services.AddScoped<IEmpleadoRepository, EmpleadoRepository>();
builder.Services.AddScoped<EmpleadoService>();
builder.Services.AddOptions<DepartamentosResilienceOptions>()
    .Bind(builder.Configuration.GetSection(DepartamentosResilienceOptions.SectionName))
    .Validate(
        o => o.EsValida(),
        "Departamentos:Resilience inválido: FallosConsecutivos debe estar entre 3 y 5 y " +
        "DuracionCircuitoAbierto entre 00:00:30 y 00:01:00.")
    .ValidateOnStart();
// Singleton: el estado del circuito (CLOSED/OPEN/HALF_OPEN) se comparte entre todas las peticiones.
builder.Services.AddSingleton<DepartamentoCircuitBreaker>();
builder.Services.AddHttpClient<IDepartamentoClient, DepartamentoClient>((sp, client) =>
{
    var baseUrl = builder.Configuration["Departamentos:BaseUrl"]
        ?? throw new InvalidOperationException("Falta la configuración Departamentos:BaseUrl.");
    client.BaseAddress = new Uri(baseUrl, UriKind.Absolute);
    client.Timeout = sp.GetRequiredService<IOptions<DepartamentosResilienceOptions>>().Value.TimeoutLlamada;
    client.DefaultRequestHeaders.Accept.Add(new System.Net.Http.Headers.MediaTypeWithQualityHeaderValue("application/json"));
});
builder.Services.AddSingleton<RegistroService.Infrastructure.Messaging.IEventPublisher, RegistroService.Infrastructure.Messaging.RabbitMqPublisher>();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// PRIMER middleware del pipeline: nada debe escaparse sin traducir.
app.UseExceptionHandler(exceptionHandlerApp =>
{
    exceptionHandlerApp.Run(async httpContext =>
    {
        var feature = httpContext.Features.Get<IExceptionHandlerPathFeature>();
        var exception = feature?.Error;
        var logger = httpContext.RequestServices
            .GetRequiredService<ILoggerFactory>()
            .CreateLogger("GlobalExceptionHandler");

        var (status, mensaje) = exception switch
        {
            EmpleadoDuplicadoException
                => (StatusCodes.Status409Conflict, exception.Message),

            DepartamentoNoEncontradoException
                => (StatusCodes.Status400BadRequest, exception.Message),

            DomainException or ArgumentException
                => (StatusCodes.Status400BadRequest, exception.Message),

            // 400 — JSON malformado, tipo inválido (p. ej. fechaIngreso: "ayer")
            BadHttpRequestException bad
                => (bad.StatusCode, "La solicitud no tiene un formato válido."),

            // 503 — la dependencia HTTP no respondió  <<< EL FIX
            DepartamentosNoDisponibleException
                => (StatusCodes.Status503ServiceUnavailable, exception.Message),

            // 503 — la base de datos propia no respondió
            RetryLimitExceededException or TimeoutException
                => (StatusCodes.Status503ServiceUnavailable,
                    "La base de datos de registro no está disponible en este momento."),

            NpgsqlException and not PostgresException
                => (StatusCodes.Status503ServiceUnavailable,
                    "La base de datos de registro no está disponible en este momento."),

            DbUpdateException { InnerException: NpgsqlException and not PostgresException }
                => (StatusCodes.Status503ServiceUnavailable,
                    "La base de datos de registro no está disponible en este momento."),

            _ => (StatusCodes.Status500InternalServerError, "Ocurrió un error interno del servidor.")
        };

        if (status >= 500)
        {
            logger.LogError(exception, "Error {Status} en {Method} {Path}",
                status, httpContext.Request.Method, feature?.Path);
        }
        else
        {
            logger.LogWarning("Error {Status} en {Method} {Path}: {Mensaje}",
                status, httpContext.Request.Method, feature?.Path, exception?.Message);
        }

        httpContext.Response.StatusCode = status;
        if (status == StatusCodes.Status503ServiceUnavailable)
        {
            httpContext.Response.Headers.RetryAfter = "5";
        }

        await httpContext.Response.WriteAsJsonAsync(new { error = mensaje });
    });
});

app.UseSwagger();
app.UseSwaggerUI();

app.UseSwagger();
app.UseSwaggerUI();

app.MapGet("/health", () => Results.Ok(new { status = "healthy" }))
    .ExcludeFromDescription();

app.MapGet("/health/ready", async (RegistroDbContext db, CancellationToken ct) =>
{
    var dbOk = await db.Database.CanConnectAsync(ct);
    return dbOk
        ? Results.Ok(new { status = "ready", database = "up" })
        : Results.Json(new { status = "not_ready", database = "down" },
            statusCode: StatusCodes.Status503ServiceUnavailable);
}).ExcludeFromDescription();

// Estado del circuit breaker hacia DepartamentosService (CLOSED, OPEN o HALF_OPEN).
// No afecta a /health/ready: un circuito abierto no debe sacar a RegistroService de servicio,
// porque el fallback sigue permitiendo registrar empleados como PENDIENTE_VALIDACION.
app.MapGet("/health/circuit-breaker", (DepartamentoCircuitBreaker circuitBreaker) => Results.Ok(new
{
    dependencia = "departamentos-service",
    estado = circuitBreaker.Estado,
    fallosConsecutivos = circuitBreaker.FallosConsecutivos,
    duracionCircuitoAbiertoSegundos = circuitBreaker.DuracionCircuitoAbierto.TotalSeconds
})).ExcludeFromDescription();

// ==================== ENDPOINTS DE EMPLEADOS ====================

/// <summary>
/// POST /empleados - Registrar un nuevo empleado
/// </summary>
/// <remarks>
/// Registra un nuevo empleado en el sistema con los datos proporcionados.
///
/// Validaciones aplicadas:
/// - Todos los campos son requeridos y no pueden estar vacíos
/// - El email debe ser único en el sistema (retorna 409 si duplicado)
/// - El numeroEmpleado debe ser único en el sistema (retorna 409 si duplicado)
/// - El email se almacena automáticamente en minúsculas
/// - El departamento se verifica contra DepartamentosService con circuit breaker; si el servicio
///   no está disponible (fallback) el empleado se registra con estado PENDIENTE_VALIDACION
///
/// Ejemplo de solicitud:
/// ```json
/// {
///   "id": "E001",
///   "nombre": "Juan",
///   "apellido": "Pérez",
///   "email": "juan.perez@company.com",
///   "numeroEmpleado": "EMP001",
///   "cargo": "Ingeniero de Software",
///   "area": "Tecnología",
///   "departamentoId": "DEP-TI-001",
///   "fechaIngreso": "2024-01-15"
/// }
/// ```
/// </remarks>
/// <param name="service">Servicio de empleados (inyección de dependencias)</param>
/// <param name="request">Datos del empleado a registrar</param>
/// <param name="cancellationToken">Token de cancelación</param>
/// <returns>201 Created con los datos del empleado registrado y su URL en `Location`</returns>
/// <response code="201">Empleado registrado (estado ACTIVO, o PENDIENTE_VALIDACION si Departamentos no estaba disponible)</response>
/// <response code="400">Error de validación o departamento inexistente</response>
/// <response code="409">El email, numeroEmpleado o ID ya está registrado</response>
/// <response code="500">Error interno del servidor</response>
app.MapPost("/empleados", async (
    EmpleadoService service,
    CreateEmpleadoRequest request,
    CancellationToken cancellationToken) =>
{
    // Convertir DTO a entidad de dominio
    var empleado = request.ToEntity();

    // Registrar empleado (validaciones de negocio en el servicio)
    var empleadoRegistrado = await service.RegistrarAsync(empleado, cancellationToken);

    // Convertir entidad a DTO de respuesta
    var response = empleadoRegistrado.ToResponse();

    return Results.Created($"/empleados/{empleadoRegistrado.Id}", response);
})
.WithName("RegistrarEmpleado")
.Produces<EmpleadoResponse>(StatusCodes.Status201Created)
.Produces(StatusCodes.Status400BadRequest)
.Produces(StatusCodes.Status409Conflict)
.Produces(StatusCodes.Status500InternalServerError);

/// <summary>
/// GET /empleados/{id} - Obtener empleado por ID
/// </summary>
/// <remarks>
/// Obtiene la información completa de un empleado específico usando su identificador único.
/// </remarks>
/// <param name="service">Servicio de empleados (inyección de dependencias)</param>
/// <param name="id">Identificador único del empleado</param>
/// <param name="cancellationToken">Token de cancelación</param>
/// <returns>200 OK con los datos del empleado, o 404 Not Found si no existe</returns>
/// <response code="200">Empleado encontrado</response>
/// <response code="404">Empleado no encontrado con mensaje exacto: "El empleado con id {id} no existe"</response>
/// <response code="500">Error interno del servidor</response>
app.MapGet("/empleados/{id}", async (
    EmpleadoService service,
    string id,
    CancellationToken cancellationToken) =>
{
    var empleado = await service.BuscarPorIdAsync(id, cancellationToken);

    if (empleado is null)
    {
        return Results.Json(
            new { error = $"El empleado con id {id} no existe" },
            statusCode: StatusCodes.Status404NotFound);
    }

    var response = empleado.ToResponse();
    return Results.Ok(response);
})
.WithName("ObtenerEmpleadoPorId")
.Produces<EmpleadoResponse>(StatusCodes.Status200OK)
.Produces(StatusCodes.Status404NotFound)
.Produces(StatusCodes.Status500InternalServerError);

// ENDPOINT RETO 4: Actualización de datos de un empleado (requerido para probar el evento actualizado)

app.MapPut("/empleados/{id}", async (
    EmpleadoService service,
    string id,
    CreateEmpleadoRequest request,
    CancellationToken cancellationToken) =>
{
    var datosNuevos = new Empleado(
        id: id,
        nombre: request.Nombre,
        apellido: request.Apellido,
        email: request.Email,
        numeroEmpleado: request.NumeroEmpleado,
        cargo: request.Cargo,
        area: request.Area,
        departamentoId: request.DepartamentoId,
        fechaIngreso: request.FechaIngreso
    );
    
    var actualizado = await service.ActualizarAsync(id, datosNuevos, cancellationToken);
    
    if (actualizado == null)
        return Results.NotFound(new { error = "El empleado con id " + id + " no existe" });
        
    return Results.Ok(actualizado.ToResponse());
})
.WithName("ActualizarEmpleado")
.Produces<EmpleadoResponse>(StatusCodes.Status200OK)
.Produces(StatusCodes.Status404NotFound);

// ENDPOINT RETO 4: Auditoría. Solo retorna empleados en estado RETIRADO y filtra por fecha

app.MapGet("/empleados", async (
    EmpleadoService service,
    string? estado,
    DateTime? desde,
    DateTime? hasta,
    CancellationToken cancellationToken) =>
{
    if (estado == "RETIRADO")
    {
        var retirados = await service.ObtenerRetiradosAsync(desde, hasta, cancellationToken);
        var response = retirados.Select(e => new {
            e.Id, e.Nombre, e.Apellido, e.Email, e.NumeroEmpleado, e.Cargo, e.Area, e.DepartamentoId, e.FechaIngreso, e.Estado, e.FechaRetiro
        });
        return Results.Ok(response);
    }
    
    // Si piden otro estado u omiten, como el reto no lo especifica, podemos retornar 400 o lista vac�a.
    return Results.BadRequest(new { error = "Solo se soporta la consulta de estado RETIRADO" });
})
.WithName("ListarEmpleados")
.Produces(StatusCodes.Status200OK);

// ENDPOINT RETO 4: Baja Lógica. No borra de BD, cambia el estado a RETIRADO y emite evento

app.MapDelete("/empleados/{id}", async (
    EmpleadoService service,
    string id,
    CancellationToken cancellationToken) =>
{
    var empleado = await service.RetirarAsync(id, cancellationToken);
    
    if (empleado is null)
    {
        return Results.Json(
            new { error = $"El empleado con id {id} no existe" },
            statusCode: StatusCodes.Status404NotFound);
    }

    return Results.NoContent();
})
.WithName("RetirarEmpleado")
.Produces(StatusCodes.Status204NoContent)
.Produces(StatusCodes.Status404NotFound)
.Produces(StatusCodes.Status500InternalServerError);

// Cualquier ruta o m�todo HTTP no soportado debe retornar el mensaje exacto del reto.
app.MapFallback(() => Results.Json(
    new { error = "Recurso no encontrado" },
    statusCode: StatusCodes.Status404NotFound));

app.Run();

// Hace visible el punto de entrada a las pruebas de integración.
public partial class Program
{
}
