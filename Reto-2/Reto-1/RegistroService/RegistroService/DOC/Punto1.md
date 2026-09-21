# 📋 PUNTO 1: Arquitectura Base, Modelo Canónico y Validaciones

**Responsable:** Persona 1  
**Criterio:** Criterio 3 (1.0 pt)  
**Descripción:** Crear la solución base en C# con modelo canónico de Empleado, validaciones de negocio y persistencia en memoria.

---

## 📦 Estructura del Proyecto

```
RegistroService/
├── Domain/                                    ← Capa de Dominio (Lógica de Negocio)
│   ├── Entities/
│   │   └── Empleado.cs                       ✓ Modelo con 10 campos requeridos
│   ├── Enums/
│   │   └── EstadoEmpleado.cs                ✓ Estados posibles del empleado
│   ├── Exceptions/
│   │   ├── DomainException.cs               ✓ Excepción base
│   │   └── EmpleadoDuplicadoException.cs    ✓ Validación de duplicados
│   ├── Repositories/
│   │   ├── IEmpleadoRepository.cs           ✓ Interfaz (contrato)
│   │   └── EmpleadoRepository.cs            ✓ Implementación en memoria
│   └── Services/
│       └── EmpleadoService.cs               ✓ Lógica de validación
│
├── API/                                       ← Capa de Presentación (HTTP)
│   ├── DTOs/
│   │   ├── CreateEmpleadoRequest.cs         ✓ DTO para entrada
│   │   └── EmpleadoResponse.cs              ✓ DTO para salida
│   └── Extensions/
│       └── MappingExtensions.cs             ✓ Conversión Entidad ↔ DTO
│
├── DOC/                                       ← Documentación
│   └── Punto1.md                            ← Este archivo
│
├── Program.cs                                ✓ Configuración y Endpoints
└── RegistroService.csproj
```

---

## 🎯 Requisitos Implementados

### ✅ 1. Solución C# (ASP.NET Core Web API)

**Tipo de proyecto:** ASP.NET Core 10.0 Web API  
**Archivo:** `RegistroService.csproj`

```xml
<Project Sdk="Microsoft.NET.Sdk.Web">
    <PropertyGroup>
        <TargetFramework>net10.0</TargetFramework>
        <Nullable>enable</Nullable>
        <ImplicitUsings>enable</ImplicitUsings>
    </PropertyGroup>
</Project>
```

### ✅ 2. Modelo Empleado con 10 Campos Requeridos

**Ruta:** `Domain/Entities/Empleado.cs`

**Campos implementados:**
1. `string Id` - Identificador único
2. `string Nombre` - Nombre del empleado
3. `string Apellido` - Apellido del empleado
4. `string Email` - Email único (case-insensitive, almacenado en minúsculas)
5. `string NumeroEmpleado` - Identificador numérico único
6. `string Cargo` - Cargo en la organización
7. `string Area` - Área de trabajo
8. `string DepartamentoId` - ID del departamento
9. `DateOnly FechaIngreso` - Fecha de ingreso
10. `EstadoEmpleado Estado` - Estado del empleado

**Características:**
- Clase `sealed` (no puede ser heredada)
- Constructor con validaciones (`Requerido()`)
- Campos de solo lectura (`get;`)
- Solo `Estado` tiene setter privado
- Normalización automática de email a minúsculas
- Trim() automático en campos string

```csharp
public sealed class Empleado
{
    public string Id { get; }
    public string Nombre { get; }
    public string Apellido { get; }
    public string Email { get; }  // Almacenado en minúsculas
    public string NumeroEmpleado { get; }
    public string Cargo { get; }
    public string Area { get; }
    public string DepartamentoId { get; }
    public DateOnly FechaIngreso { get; }
    public EstadoEmpleado Estado { get; private set; }
}
```

### ✅ 3. Enum EstadoEmpleado

**Ruta:** `Domain/Enums/EstadoEmpleado.cs`

```csharp
public enum EstadoEmpleado
{
    Activo,         // ← Todos los empleados se registran así (Reto 1)
    EnVacaciones,
    Retirado
}
```

**En Reto 1:** Todos los empleados registrados tienen estado `Activo` automáticamente en el constructor.

### ✅ 4. Servicio y Repositorio en Memoria

#### 4.1 Interfaz del Repositorio

**Ruta:** `Domain/Repositories/IEmpleadoRepository.cs`

```csharp
public interface IEmpleadoRepository
{
    Task<Empleado?> ObtenerPorIdAsync(string id, CancellationToken cancellationToken = default);
    Task<bool> ExisteEmailAsync(string email, CancellationToken cancellationToken = default);
    Task<bool> ExisteNumeroEmpleadoAsync(string numeroEmpleado, CancellationToken cancellationToken = default);
    Task RegistrarAsync(Empleado empleado, CancellationToken cancellationToken = default);
}
```

#### 4.2 Implementación en Memoria

**Ruta:** `Domain/Repositories/EmpleadoRepository.cs`

**Tecnología:** `Dictionary<string, Empleado>` + `HashSet<string>` con `lock` explícito

- ✅ Thread-safe (sincronización explícita con `lock`)
- ✅ Sin dependencias externas (en memoria)
- ✅ Ideal para pruebas y desarrollo
- ✅ Datos persisten durante la sesión de la aplicación

```csharp
public sealed class EmpleadoRepository : IEmpleadoRepository
{
    private readonly Dictionary<string, Empleado> _empleados = new();
    private readonly HashSet<string> _emails = new(StringComparer.OrdinalIgnoreCase);
    private readonly HashSet<string> _numerosEmpleado = new(StringComparer.Ordinal);
    private readonly object _sync = new();
    
    public async Task<bool> ExisteEmailAsync(string email, CancellationToken cancellationToken = default)
    {
        lock (_sync)
        {
            return Task.FromResult(_emails.Contains(email.Trim()));
        }
    }
    
    public async Task<bool> ExisteNumeroEmpleadoAsync(string numeroEmpleado, CancellationToken cancellationToken = default)
    {
        lock (_sync)
        {
            return Task.FromResult(_numerosEmpleado.Contains(numeroEmpleado.Trim()));
        }
    }
}
```

#### 4.3 Servicio de Negocio

**Ruta:** `Domain/Services/EmpleadoService.cs`

- Inyecta el repositorio
- Valida reglas de negocio antes de guardar
- Lanza excepciones en caso de violación de restricciones

```csharp
public class EmpleadoService
{
    private readonly IEmpleadoRepository _repository;
    
    public async Task<Empleado> RegistrarAsync(Empleado empleado, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(empleado);
        
        // ✓ VALIDACIÓN 1: Email único
        if (await _repository.ExisteEmailAsync(empleado.Email, cancellationToken))
        {
            throw new EmpleadoDuplicadoException("email", empleado.Email);
        }
        
        // ✓ VALIDACIÓN 2: NumeroEmpleado único
        if (await _repository.ExisteNumeroEmpleadoAsync(empleado.NumeroEmpleado, cancellationToken))
        {
            throw new EmpleadoDuplicadoException("numeroEmpleado", empleado.NumeroEmpleado);
        }
        
        await _repository.RegistrarAsync(empleado, cancellationToken);
        return empleado;
    }
}
```

---

## ✅ 5. Validaciones de Negocio

### Validación 1: Email Duplicado → 400 Bad Request

**Implementación:**
1. `EmpleadoService.RegistrarAsync()` llama a `_repository.ExisteEmailAsync()`
2. Si existe → Lanza `EmpleadoDuplicadoException`
3. Middleware captura la excepción → Retorna **400 Bad Request**

**Ruta de archivos:**
- `Domain/Services/EmpleadoService.cs` (línea ~25)
- `Domain/Repositories/EmpleadoRepository.cs` (línea ~53)
- `Domain/Exceptions/EmpleadoDuplicadoException.cs`
- `Program.cs` - Middleware de manejo de excepciones (línea ~28)

**Respuesta HTTP (400 Bad Request):**
```
Ya existe un empleado con email 'juan.perez@company.com'.
```

**Respuesta HTTP (400 Bad Request):**
```
Ya existe un empleado con numeroEmpleado 'EMP001'.
```

**Implementación:**
1. `EmpleadoService.RegistrarAsync()` llama a `_repository.ExisteNumeroEmpleadoAsync()`
2. Si existe → Lanza `EmpleadoDuplicadoException`
3. Middleware captura la excepción → Retorna **400 Bad Request**

**Ruta de archivos:**
- `Domain/Services/EmpleadoService.cs` (línea ~30)
- `Domain/Repositories/EmpleadoRepository.cs` (línea ~65)
- `Domain/Exceptions/EmpleadoDuplicadoException.cs`
- `Program.cs` - Middleware de manejo de excepciones (línea ~28)

**Respuesta HTTP (400 Bad Request):**
```
Ya existe un empleado con numeroEmpleado 'EMP001'.
```

---

## 📡 DTOs (Data Transfer Objects)

### CreateEmpleadoRequest

**Ruta:** `API/DTOs/CreateEmpleadoRequest.cs`

DTO para la solicitud POST de registro de empleado. Contiene los 9 campos necesarios (el ID se genera automáticamente).

```csharp
public sealed class CreateEmpleadoRequest
{
    public string Nombre { get; set; }           // Requerido
    public string Apellido { get; set; }         // Requerido
    public string Email { get; set; }            // Requerido, único
    public string NumeroEmpleado { get; set; }   // Requerido, único
    public string Cargo { get; set; }            // Requerido
    public string Area { get; set; }             // Requerido
    public string DepartamentoId { get; set; }   // Requerido
    public DateOnly FechaIngreso { get; set; }   // Requerido
}
```

### EmpleadoResponse

**Ruta:** `API/DTOs/EmpleadoResponse.cs`

DTO para la respuesta HTTP con los datos completos del empleado registrado, incluyendo ID y Estado.

```csharp
public sealed class EmpleadoResponse
{
    public string Id { get; set; }               // Generado automáticamente
    public string Nombre { get; set; }
    public string Apellido { get; set; }
    public string Email { get; set; }            // En minúsculas
    public string NumeroEmpleado { get; set; }
    public string Cargo { get; set; }
    public string Area { get; set; }
    public string DepartamentoId { get; set; }
    public DateOnly FechaIngreso { get; set; }
    public EstadoEmpleado Estado { get; set; }   // Siempre "Activo" en Reto 1
}
```

---

## 🔄 MappingExtensions

**Ruta:** `API/Extensions/MappingExtensions.cs`

Métodos de extensión para convertir entre DTOs y entidades del dominio.

```csharp
public static class MappingExtensions
{
    // CreateEmpleadoRequest → Empleado (entidad del dominio)
    public static Empleado ToEntity(this CreateEmpleadoRequest request)
    {
        return new Empleado(
            id: request.Id,  // ← ID canónico recibido
            nombre: request.Nombre,
            apellido: request.Apellido,
            email: request.Email,
            numeroEmpleado: request.NumeroEmpleado,
            cargo: request.Cargo,
            area: request.Area,
            departamentoId: request.DepartamentoId,
            fechaIngreso: request.FechaIngreso
        );
    }
    
    // Empleado → EmpleadoResponse (para retornar en HTTP)
    public static EmpleadoResponse ToResponse(this Empleado empleado)
    {
        return new EmpleadoResponse
        {
            Id = empleado.Id,
            Nombre = empleado.Nombre,
            // ... resto de mapeos
        };
    }
}
```

---

## 🛣️ Endpoints REST

### POST /empleados - Registrar Empleado

**Ruta:** `Program.cs` (línea ~82)

**Solicitud:**
```http
POST /empleados HTTP/1.1
Content-Type: application/json

{
  "nombre": "Juan",
  "apellido": "Pérez",
  "email": "juan.perez@company.com",
  "numeroEmpleado": "EMP001",
  "cargo": "Ingeniero de Software",
  "area": "Tecnología",
  "departamentoId": "DEP-TI-001",
  "fechaIngreso": "2024-01-15"
}
```

**Respuesta Exitosa (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Juan",
  "apellido": "Pérez",
  "email": "juan.perez@company.com",
  "numeroEmpleado": "EMP001",
  "cargo": "Ingeniero de Software",
  "area": "Tecnología",
  "departamentoId": "DEP-TI-001",
  "fechaIngreso": "2024-01-15",
  "estado": "Activo"
}
```

**Respuesta - Email Duplicado (400 Bad Request):**
```json
{
  "error": "Ya existe un empleado con email 'juan.perez@company.com'.",
  "campo": "email",
  "valor": "juan.perez@company.com",
  "timestamp": "2024-08-05T22:35:00Z"
}
```

**Respuesta - NumeroEmpleado Duplicado (400 Bad Request):**
```json
{
  "error": "Ya existe un empleado con numeroEmpleado 'EMP001'.",
  "campo": "numeroEmpleado",
  "valor": "EMP001",
  "timestamp": "2024-08-05T22:35:00Z"
}
```

---

### GET /empleados/{id} - Obtener Empleado

**Ruta:** `Program.cs` (línea ~113)

**Solicitud:**
```http
GET /empleados/550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
```

**Respuesta Exitosa (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Juan",
  "apellido": "Pérez",
  "email": "juan.perez@company.com",
  "numeroEmpleado": "EMP001",
  "cargo": "Ingeniero de Software",
  "area": "Tecnología",
  "departamentoId": "DEP-TI-001",
  "fechaIngreso": "2024-01-15",
  "estado": "Activo"
}
```

**Respuesta - No Encontrado (404 Not Found):**
```json
{
  "error": "Empleado con ID 'invalid-id' no encontrado."
}
```

---

## 🔧 Configuración de Inyección de Dependencias

**Ruta:** `Program.cs` (línea ~16)

```csharp
// Registrar implementaciones
builder.Services.AddSingleton<IEmpleadoRepository, EmpleadoRepository>();
builder.Services.AddScoped<EmpleadoService>();
```

**Ciclos de vida:**
- `IEmpleadoRepository` → **Singleton** (una sola instancia en memoria durante toda la ejecución)
- `EmpleadoService` → **Scoped** (nueva instancia por cada request HTTP)

---

## 🛡️ Middleware de Manejo de Excepciones

**Ruta:** `Program.cs` (línea ~28)

Captura excepciones del dominio y las convierte en respuestas HTTP apropiadas.

```csharp
app.UseExceptionHandler(exceptionHandlerApp =>
{
    exceptionHandlerApp.Run(async httpContext =>
    {
        var exception = httpContext.Features.Get<IExceptionHandlerPathFeature>()?.Error;
        
        if (exception is EmpleadoDuplicadoException duplicado)
        {
            httpContext.Response.StatusCode = StatusCodes.Status400BadRequest;
            httpContext.Response.ContentType = "text/plain; charset=utf-8";
            await httpContext.Response.WriteAsync(duplicado.Message);
        }
        else if (exception is ArgumentException)
        {
            httpContext.Response.StatusCode = StatusCodes.Status400BadRequest;
            httpContext.Response.ContentType = "text/plain; charset=utf-8";
            await httpContext.Response.WriteAsync(exception.Message);
        }
        else
        {
            httpContext.Response.StatusCode = StatusCodes.Status500InternalServerError;
            httpContext.Response.ContentType = "text/plain; charset=utf-8";
            await httpContext.Response.WriteAsync("Ocurrió un error interno del servidor.");
        }
    });
});
```

---

## 📝 Documentación - XML Comments (C# Equivalent to JavaDoc)

**Aplicado a:**
- ✅ Todas las clases públicas
- ✅ Todos los métodos públicos
- ✅ Todos los parámetros (`<param>`)
- ✅ Todos los retornos (`<returns>`)
- ✅ Notas técnicas (`<remarks>`)
- ✅ Excepciones (`<exception>`)

**Ejemplo - Archivo: `Domain/Repositories/EmpleadoRepository.cs`**
```csharp
/// <summary>
/// Implementación en memoria del repositorio de empleados.
/// Utiliza ConcurrentDictionary para acceso thread-safe.
/// </summary>
public sealed class EmpleadoRepository : IEmpleadoRepository
{
    /// <summary>
    /// Verifica si existe un empleado con el email especificado.
    /// </summary>
    /// <param name="email">Email a buscar (será convertido a minúsculas).</param>
    /// <param name="cancellationToken">Token de cancelación.</param>
    /// <returns>true si el email existe, false en caso contrario.</returns>
    public Task<bool> ExisteEmailAsync(
        string email,
        CancellationToken cancellationToken = default)
    {
        // Implementación...
    }
}
```

---

## 📊 Diagrama de Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                     API REST                                │
│        POST /empleados (CreateEmpleadoRequest)          │
│        GET  /empleados/{id}                             │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│             PRESENTATION LAYER (Program.cs)                 │
│    - Endpoints Mapping (Minimal APIs)                       │
│    - Middleware de Excepciones                              │
│    - Inyección de Dependencias                              │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                  API.DTOs                                   │
│    - CreateEmpleadoRequest (entrada)                        │
│    - EmpleadoResponse (salida)                              │
│    - MappingExtensions (conversión)                         │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│            DOMAIN LAYER (Lógica de Negocio)                 │
│                                                              │
│  ┌─ Services                                                │
│  │  - EmpleadoService (validaciones de negocio)            │
│  │                                                          │
│  ├─ Repositories                                            │
│  │  - IEmpleadoRepository (interfaz)                       │
│  │  - EmpleadoRepository (implementación en memoria)       │
│  │                                                          │
│  ├─ Entities                                                │
│  │  - Empleado (modelo canónico con 10 campos)            │
│  │                                                          │
│  ├─ Enums                                                   │
│  │  - EstadoEmpleado (Activo, EnVacaciones, Retirado)     │
│  │                                                          │
│  └─ Exceptions                                              │
│     - DomainException (base)                                │
│     - EmpleadoDuplicadoException                            │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│        PERSISTENCE LAYER (En Memoria)                       │
│    - ConcurrentDictionary<string, Empleado>                │
│    - Thread-safe                                            │
│    - Datos persisten durante sesión                         │
└──────────────────────────────────────────────────────────────┘
```

---

## ✅ Validación de Requisitos

| Requisito | Implementado | Archivo/Línea |
|-----------|--------------|---------------|
| Solución en C# | ✅ | `RegistroService.csproj` |
| Modelo Empleado (10 campos) | ✅ | `Domain/Entities/Empleado.cs` |
| Enum EstadoEmpleado | ✅ | `Domain/Enums/EstadoEmpleado.cs` |
| Servicio en memoria | ✅ | `Domain/Repositories/EmpleadoRepository.cs` |
| Validación: Email único (400) | ✅ | `EmpleadoService.cs:25-28` |
| Validación: NumeroEmpleado único (400) | ✅ | `EmpleadoService.cs:30-36` |
| DTOs | ✅ | `API/DTOs/*.cs` |
| Endpoints REST | ✅ | `Program.cs:82-140` |
| Middleware excepciones | ✅ | `Program.cs:28-61` |
| Documentación XML Comments | ✅ | Todos los archivos |

---

## 🚀 Cómo Compilar y Ejecutar

```bash
# Compilar
cd RegistroService
dotnet build

# Ejecutar
dotnet run

# La API estará disponible en: https://localhost:5001
# Swagger/OpenAPI en: https://localhost:5001/openapi/v1.json
```

---

## 📚 Tecnologías Utilizadas

- **.NET 10.0** - Framework
- **C# 13** - Lenguaje de programación
- **ASP.NET Core** - Web API
- **Minimal APIs** - Patrón de endpoints
- **ConcurrentDictionary** - Persistencia en memoria thread-safe
- **OpenAPI/Swagger** - Documentación automática de API
- **XML Documentation Comments** - Documentación de código (JavaDoc equivalente)

---

## 📌 Notas Importantes

1. **Email normalization**: El email se almacena siempre en minúsculas para evitar duplicados sensibles a mayúsculas.
2. **Thread-safe**: Sincronización explícita con `lock` para garantizar atomicidad en operaciones concurrentes.
3. **Async/Await**: Todos los métodos del repositorio son asincronos, preparados para integración futura con base de datos.
4. **Validación de negocio**: Las excepciones se lanzan en la capa de servicio, no en el repositorio.
5. **Inyección de dependencias**: Facilita testing y cambio de implementaciones en el futuro.

---

## 👤 Autor

**Persona 1**  
**Fecha:** 2024-08-05  
**Criterio:** 3 (Arquitectura Base, Modelo Canónico y Validaciones)
