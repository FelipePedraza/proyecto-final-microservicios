using RegistroService.Domain.Entities;
using RegistroService.Domain.Repositories;
using RegistroService.Domain.Services;
using RegistroService.Domain.Exceptions;
using RegistroService.API.DTOs;
using RegistroService.API.Extensions;
using RegistroService.API.OpenApi;
using Microsoft.AspNetCore.Diagnostics;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using RegistroService.Infrastructure.Departamentos;
using RegistroService.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore.Storage;
using Microsoft.OpenApi;
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
builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new Microsoft.OpenApi.OpenApiInfo
    {
        Title = "RegistroService API",
        Version = "v1",
        Description = "API para registrar, consultar, actualizar y retirar empleados. " +
            "El esquema Bearer se documenta para operaciones /empleados protegidas por el Gateway; " +
            "RegistroService no valida JWT directamente."
    });
    options.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT",
        Description = "JWT Bearer token validado por el Gateway."
    });
    options.OperationFilter<BearerAuthOperationFilter>();
    var xmlDocumentation = Path.Combine(
        AppContext.BaseDirectory,
        $"{typeof(Program).Assembly.GetName().Name}.xml");
    if (File.Exists(xmlDocumentation))
    {
        options.IncludeXmlComments(xmlDocumentation);
    }
});

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

        await httpContext.Response.WriteAsJsonAsync(new ErrorResponse(mensaje));
    });
});

app.UseSwagger();
app.UseSwaggerUI();

app.MapGet("/health", () => Results.Ok(new HealthResponse("healthy")))
    .WithTags("Health")
    .WithName("Health")
    .WithSummary("Comprueba que RegistroService está activo.")
    .WithDescription("Endpoint de liveness. No consulta la base de datos.")
    .Produces<HealthResponse>(StatusCodes.Status200OK);

app.MapGet("/health/ready", async (RegistroDbContext db, CancellationToken ct) =>
{
    var dbOk = await db.Database.CanConnectAsync(ct);
    return dbOk
        ? Results.Ok(new ReadinessResponse("ready", "up"))
        : Results.Json(new ReadinessResponse("not_ready", "down"),
            statusCode: StatusCodes.Status503ServiceUnavailable);
})
    .WithTags("Health")
    .WithName("Readiness")
    .WithSummary("Comprueba la disponibilidad de la base de datos.")
    .WithDescription("Devuelve 200 cuando la base de datos responde o 503 cuando no está lista. " +
        "Un fallo de infraestructura también puede producir el error estándar { error }.")
    .Produces<ReadinessResponse>(StatusCodes.Status200OK)
    .Produces<ReadinessResponse>(StatusCodes.Status503ServiceUnavailable);

// Estado del circuit breaker hacia DepartamentosService (CLOSED, OPEN o HALF_OPEN).
// No afecta a /health/ready: un circuito abierto no debe sacar a RegistroService de servicio,
// porque el fallback sigue permitiendo registrar empleados como PENDIENTE_VALIDACION.
app.MapGet("/health/circuit-breaker", (DepartamentoCircuitBreaker circuitBreaker) =>
    Results.Ok(new CircuitBreakerHealthResponse(
        "departamentos-service",
        circuitBreaker.Estado,
        circuitBreaker.FallosConsecutivos,
        circuitBreaker.DuracionCircuitoAbierto.TotalSeconds)))
    .WithTags("Health")
    .WithName("EstadoCircuitBreaker")
    .WithSummary("Consulta el estado del circuit breaker de DepartamentosService.")
    .Produces<CircuitBreakerHealthResponse>(StatusCodes.Status200OK);

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
.WithTags("Empleados")
.WithSummary("Registra un empleado.")
.WithMetadata(new BearerAuthRequiredMetadata())
.WithDescription("El correo se normaliza a minúsculas. El departamento se verifica con DepartamentosService; " +
    "si no está disponible, el registro se completa con estado PENDIENTE_VALIDACION. " +
    "El identificador devuelto también se incluye en la cabecera Location.")
.Accepts<CreateEmpleadoRequest>("application/json")
.Produces<EmpleadoResponse>(StatusCodes.Status201Created, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status400BadRequest, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status409Conflict, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status503ServiceUnavailable, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status500InternalServerError, "application/json");

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
            new ErrorResponse($"El empleado con id {id} no existe"),
            statusCode: StatusCodes.Status404NotFound);
    }

    var response = empleado.ToResponse();
    return Results.Ok(response);
})
.WithName("ObtenerEmpleadoPorId")
.WithTags("Empleados")
.WithSummary("Obtiene un empleado por ID.")
.WithMetadata(new BearerAuthRequiredMetadata())
.WithDescription("El parámetro id es el identificador único del empleado.")
.Produces<EmpleadoResponse>(StatusCodes.Status200OK, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status404NotFound, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status503ServiceUnavailable, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status500InternalServerError, "application/json");

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
        return Results.NotFound(new ErrorResponse("El empleado con id " + id + " no existe"));
        
    return Results.Ok(actualizado.ToResponse());
})
.WithName("ActualizarEmpleado")
.WithTags("Empleados")
.WithSummary("Reemplaza los datos de un empleado existente.")
.WithMetadata(new BearerAuthRequiredMetadata())
.WithDescription("El ID de ruta identifica el registro que se actualiza; el campo id del cuerpo se ignora. " +
    "El cuerpo utiliza el mismo contrato que al registrar un empleado.")
.Accepts<CreateEmpleadoRequest>("application/json")
.Produces<EmpleadoResponse>(StatusCodes.Status200OK, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status400BadRequest, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status404NotFound, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status503ServiceUnavailable, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status500InternalServerError, "application/json");

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
        var response = retirados.Select(e => new RetiredEmpleadoResponse(
            e.Id,
            e.Nombre,
            e.Apellido,
            e.Email,
            e.NumeroEmpleado,
            e.Cargo,
            e.Area,
            e.DepartamentoId,
            e.FechaIngreso,
            e.Estado,
            e.FechaRetiro));
        return Results.Ok(response);
    }
    
    // Si piden otro estado u omiten, como el reto no lo especifica, podemos retornar 400 o lista vac�a.
    return Results.BadRequest(new ErrorResponse("Solo se soporta la consulta de estado RETIRADO"));
})
.WithName("ListarEmpleados")
.WithTags("Empleados")
.WithSummary("Consulta empleados retirados para auditoría.")
.WithMetadata(new BearerAuthRequiredMetadata())
.WithDescription("Solo se admite estado=RETIRADO. Los parámetros desde y hasta son límites opcionales " +
    "del rango de fecha de retiro, en formato de fecha y hora ISO 8601.")
.Produces<IEnumerable<RetiredEmpleadoResponse>>(StatusCodes.Status200OK, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status400BadRequest, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status503ServiceUnavailable, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status500InternalServerError, "application/json");

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
            new ErrorResponse($"El empleado con id {id} no existe"),
            statusCode: StatusCodes.Status404NotFound);
    }

    return Results.NoContent();
})
.WithName("RetirarEmpleado")
.WithTags("Empleados")
.WithSummary("Retira lógicamente un empleado.")
.WithMetadata(new BearerAuthRequiredMetadata())
.WithDescription("No elimina el registro: actualiza su estado a RETIRADO y publica el evento correspondiente.")
.Produces(StatusCodes.Status204NoContent)
.Produces<ErrorResponse>(StatusCodes.Status404NotFound, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status503ServiceUnavailable, "application/json")
.Produces<ErrorResponse>(StatusCodes.Status500InternalServerError, "application/json");

// Cualquier ruta o m�todo HTTP no soportado debe retornar el mensaje exacto del reto.
app.MapFallback(() => Results.Json(
    new ErrorResponse("Recurso no encontrado"),
    statusCode: StatusCodes.Status404NotFound))
    .ExcludeFromDescription();

app.Run();

// Hace visible el punto de entrada a las pruebas de integración.
public partial class Program
{
}
