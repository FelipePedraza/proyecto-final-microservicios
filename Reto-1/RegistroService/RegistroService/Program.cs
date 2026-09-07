using RegistroService.Domain.Repositories;
using RegistroService.Domain.Services;
using RegistroService.Domain.Exceptions;
using RegistroService.API.DTOs;
using RegistroService.API.Extensions;
using Microsoft.AspNetCore.Diagnostics;
using Microsoft.EntityFrameworkCore;
using RegistroService.Infrastructure.Departamentos;
using RegistroService.Infrastructure.Persistence;

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
});
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

if (!app.Environment.IsEnvironment("Testing"))
{
    using var scope = app.Services.CreateScope();
    var dbContext = scope.ServiceProvider.GetRequiredService<RegistroDbContext>();
    dbContext.Database.EnsureCreated();
}

app.UseSwagger();
app.UseSwaggerUI();


// Middleware de manejo global de excepciones
// Captura excepciones del dominio y las convierte en respuestas HTTP apropiadas
app.UseExceptionHandler(exceptionHandlerApp =>
{
    exceptionHandlerApp.Run(async httpContext =>
    {
        var exception = httpContext.Features.Get<IExceptionHandlerPathFeature>()?.Error;

        if (exception is DomainException or ArgumentException)
        {
            httpContext.Response.StatusCode = StatusCodes.Status400BadRequest;
            await httpContext.Response.WriteAsJsonAsync(new { error = exception.Message });
            return;
        }
        // Manejar otras excepciones con un error genérico
        httpContext.Response.StatusCode = StatusCodes.Status500InternalServerError;
        await httpContext.Response.WriteAsJsonAsync(new { error = "Ocurrió un error interno del servidor." });
    });
});

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
        return Results.Text(
            $"El empleado con id {id} no existe",
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
app.MapFallback(() => Results.Text(
    "Recurso no encontrado",
    statusCode: StatusCodes.Status404NotFound));

app.Run();

// Hace visible el punto de entrada a las pruebas de integración.
public partial class Program
{
}
