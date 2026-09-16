using RegistroService.Domain.Repositories;
using RegistroService.Domain.Services;
using RegistroService.Domain.Exceptions;
using RegistroService.API.DTOs;
using RegistroService.API.Extensions;
using Microsoft.AspNetCore.Diagnostics;
using Microsoft.EntityFrameworkCore;
using RegistroService.Infrastructure.Departamentos;
using RegistroService.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore.Storage;
using Npgsql;
using RegistroService.Infrastructure.Departamentos;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddDbContext<RegistroDbContext>(options =>
    options.UseNpgsql(builder.Configuration.GetConnectionString("Registro")));
builder.Services.AddScoped<IEmpleadoRepository, EmpleadoRepository>();
builder.Services.AddScoped<EmpleadoService>();
builder.Services.AddHttpClient<IDepartamentoClient, DepartamentoClient>(client =>
{
    var baseUrl = builder.Configuration["Departamentos:BaseUrl"]
        ?? throw new InvalidOperationException("Falta la configuración Departamentos:BaseUrl.");
    client.BaseAddress = new Uri(baseUrl, UriKind.Absolute);
    client.Timeout = TimeSpan.FromSeconds(5);
    client.DefaultRequestHeaders.Accept.Add(new System.Net.Http.Headers.MediaTypeWithQualityHeaderValue("application/json"));
});
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
            // 400 — reglas de negocio y validación de argumentos
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

builder.Services.AddDbContext<RegistroDbContext>(options =>
    options.UseNpgsql(
        builder.Configuration.GetConnectionString("Registro"),
        npgsql => npgsql.EnableRetryOnFailure(
            maxRetryCount: 3,
            maxRetryDelay: TimeSpan.FromSeconds(2),
            errorCodesToAdd: null)));

if (!app.Environment.IsEnvironment("Testing"))
{
    using var scope = app.Services.CreateScope();
    var dbContext = scope.ServiceProvider.GetRequiredService<RegistroDbContext>();
    var startupLogger = scope.ServiceProvider.GetRequiredService<ILogger<Program>>();

    for (var intento = 1; intento <= 10; intento++)
    {
        try
        {
            dbContext.Database.EnsureCreated();
            break;
        }
        catch (NpgsqlException ex) when (intento < 10)
        {
            startupLogger.LogWarning(ex,
                "BD no lista (intento {Intento}/10). Reintentando en 3 s...", intento);
            Thread.Sleep(TimeSpan.FromSeconds(3));
        }
    }
}

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

// ==================== ENDPOINTS DE EMPLEADOS ====================

/// <summary>
/// POST /empleados - Registrar un nuevo empleado
/// </summary>
/// <remarks>
/// Registra un nuevo empleado en el sistema con los datos proporcionados.
///
/// Validaciones aplicadas:
/// - Todos los campos son requeridos y no pueden estar vacíos
/// - El email debe ser único en el sistema (retorna 400 si duplicado)
/// - El numeroEmpleado debe ser único en el sistema (retorna 400 si duplicado)
/// - El email se almacena automáticamente en minúsculas
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
/// <returns>200 OK con los datos del empleado registrado</returns>
/// <response code="200">Empleado registrado exitosamente</response>
/// <response code="400">Error de validación (email o numeroEmpleado duplicado, campos inválidos)</response>
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

    return Results.Ok(response);
})
.WithName("RegistrarEmpleado")
.Produces<EmpleadoResponse>(StatusCodes.Status200OK)
.Produces(StatusCodes.Status400BadRequest)
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

// Cualquier ruta o método HTTP no soportado debe retornar el mensaje exacto del reto.
app.MapFallback(() => Results.Json(
    new { error = "Recurso no encontrado" },
    statusCode: StatusCodes.Status404NotFound));

app.Run();

// Hace visible el punto de entrada a las pruebas de integración.
public partial class Program
{
}
