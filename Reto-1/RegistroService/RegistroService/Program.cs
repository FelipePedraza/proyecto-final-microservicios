using RegistroService.Domain.Repositories;
using RegistroService.Domain.Services;
using RegistroService.Domain.Exceptions;
using RegistroService.API.DTOs;
using RegistroService.API.Extensions;
using Microsoft.AspNetCore.Diagnostics;

var builder = WebApplication.CreateBuilder(args);

// Configurar servicios de la aplicación
// Learn more about configuring OpenAPI at https://aka.ms/aspnet/openapi
builder.Services.AddOpenApi();

// Registrar dependencias del dominio
// La inyección de dependencias permite desacoplar la lógica de negocio de la presentación
builder.Services.AddSingleton<IEmpleadoRepository, EmpleadoRepository>();
builder.Services.AddScoped<EmpleadoService>();

var app = builder.Build();

// Configurar la tubería de solicitud HTTP (HTTP request pipeline)
if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseHttpsRedirection();

// Middleware de manejo global de excepciones
// Captura excepciones del dominio y las convierte en respuestas HTTP apropiadas
app.UseExceptionHandler(exceptionHandlerApp =>
{
    exceptionHandlerApp.Run(async httpContext =>
    {
        var exception = httpContext.Features.Get<IExceptionHandlerPathFeature>()?.Error;

        if (exception is EmpleadoDuplicadoException duplicado)
        {
            httpContext.Response.StatusCode = StatusCodes.Status400BadRequest;
            httpContext.Response.ContentType = "application/json";

            await httpContext.Response.WriteAsJsonAsync(new
            {
                error = duplicado.Message,
                campo = duplicado.Campo,
                valor = duplicado.Valor,
                timestamp = DateTime.UtcNow
            });
            return;
        }

        // Manejar otras excepciones con un error genérico
        httpContext.Response.StatusCode = StatusCodes.Status500InternalServerError;
        httpContext.Response.ContentType = "application/json";

        await httpContext.Response.WriteAsJsonAsync(new
        {
            error = "Ocurrió un error interno del servidor.",
            timestamp = DateTime.UtcNow
        });
    });
});

// ==================== ENDPOINTS DE EMPLEADOS ====================

/// <summary>
/// POST /api/empleados - Registrar un nuevo empleado
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
/// <returns>201 Created con los datos del empleado registrado</returns>
/// <response code="201">Empleado registrado exitosamente</response>
/// <response code="400">Error de validación (email o numeroEmpleado duplicado, campos inválidos)</response>
/// <response code="500">Error interno del servidor</response>
app.MapPost("/api/empleados", async (
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

    return Results.Created($"/api/empleados/{response.Id}", response);
})
.WithName("RegistrarEmpleado")
.WithOpenApi()
.Produces<EmpleadoResponse>(StatusCodes.Status201Created)
.Produces(StatusCodes.Status400BadRequest)
.Produces(StatusCodes.Status500InternalServerError);

/// <summary>
/// GET /api/empleados/{id} - Obtener empleado por ID
/// </summary>
/// <remarks>
/// Obtiene la información completa de un empleado específico usando su identificador único.
/// </remarks>
/// <param name="service">Servicio de empleados (inyección de dependencias)</param>
/// <param name="id">Identificador único del empleado</param>
/// <param name="cancellationToken">Token de cancelación</param>
/// <returns>200 OK con los datos del empleado, o 404 Not Found si no existe</returns>
/// <response code="200">Empleado encontrado</response>
/// <response code="404">Empleado no encontrado</response>
/// <response code="500">Error interno del servidor</response>
app.MapGet("/api/empleados/{id}", async (
    EmpleadoService service,
    string id,
    CancellationToken cancellationToken) =>
{
    var empleado = await service.BuscarPorIdAsync(id, cancellationToken);

    if (empleado is null)
    {
        return Results.NotFound(new { error = $"Empleado con ID '{id}' no encontrado." });
    }

    var response = empleado.ToResponse();
    return Results.Ok(response);
})
.WithName("ObtenerEmpleadoPorId")
.WithOpenApi()
.Produces<EmpleadoResponse>(StatusCodes.Status200OK)
.Produces(StatusCodes.Status404NotFound)
.Produces(StatusCodes.Status500InternalServerError);

app.Run();
