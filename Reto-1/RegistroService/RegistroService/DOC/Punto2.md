# Punto 2: Endpoints HTTP y Control de Rutas (API Routing)

**Criterios:** 1 y 2 (2.0 pts)  
**Estado:** ✅ Completado  
**Fecha:** 2026-08-05

---

## 📋 Resumen de Requisitos

| Requisito | Descripción | Estado |
|-----------|-------------|--------|
| **Criterio 1** | POST /empleados con respuesta 200 OK | ✅ |
| **Criterio 2** | GET /empleados/{id} con 404 exacto y middleware para rutas no soportadas | ✅ |
| **HTTP Status Codes** | Códigos HTTP correctos según especificación | ✅ |
| **Routing Control** | Captura de rutas indefinidas con middleware | ✅ |

---

## 🔍 Implementación Detallada

### 1. Endpoint POST /api/empleados

**Ubicación:** `Program.cs` líneas 115-135

**Características:**
- ✅ Retorna **200 OK** (no 201 Created) con información del empleado registrado
- ✅ Valida email único (retorna 400 Bad Request si duplicado)
- ✅ Valida numeroEmpleado único (retorna 400 Bad Request si duplicado)
- ✅ Convierte DTO a entidad de dominio
- ✅ Usa inyección de dependencias (EmpleadoService)

**Respuesta exitosa (200 OK):**
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
  "estado": 0
}
```

**Respuesta con error (400 Bad Request):**
```json
{
  "error": "El email ya existe en el sistema",
  "campo": "email",
  "valor": "juan.perez@company.com",
  "timestamp": "2026-08-05T23:15:37.477Z"
}
```

---

### 2. Endpoint GET /api/empleados/{id}

**Ubicación:** `Program.cs` líneas 150-169

**Características:**
- ✅ Retorna **200 OK** si el empleado existe
- ✅ Retorna **404 Not Found** con mensaje exacto: `"El empleado con id {id} no existe"`
- ✅ Busca por ID único del empleado
- ✅ Respeta parámetro {id} variable

**Respuesta exitosa (200 OK):**
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
  "estado": 0
}
```

**Respuesta con error (404 Not Found):**
```json
{
  "error": "El empleado con id invalid-id no existe"
}
```

---

### 3. Middleware para Rutas No Soportadas

**Ubicación:** `Program.cs` líneas 64-78

**Características:**
- ✅ Captura **cualquier ruta o método HTTP no definido**
- ✅ Retorna **404 Not Found** con mensaje exacto: `"Recurso no encontrado"`
- ✅ Se ejecuta después de todos los endpoints
- ✅ Verifica `context.Response.StatusCode == 404`

**Respuesta (404 Not Found):**
```json
{
  "error": "Recurso no encontrado"
}
```

**Ejemplos de rutas que activan este middleware:**
- `GET /api/empleados/extra`
- `POST /api/invalid`
- `PUT /empleados/123` (método no soportado)
- `DELETE /api/empleados` (no definido)

---

## 📁 Estructura de Archivos

```
RegistroService/
├── Program.cs                              [Endpoints y middleware]
├── Domain/
│   ├── Entities/
│   │   └── Empleado.cs                    [Modelo con 10 campos]
│   ├── Exceptions/
│   │   ├── DomainException.cs
│   │   └── EmpleadoDuplicadoException.cs
│   ├── Repositories/
│   │   ├── IEmpleadoRepository.cs         [Interfaz]
│   │   └── EmpleadoRepository.cs          [ConcurrentDictionary]
│   ├── Services/
│   │   └── EmpleadoService.cs             [Lógica de negocio]
│   └── Enums/
│       └── EstadoEmpleado.cs
├── API/
│   ├── DTOs/
│   │   ├── CreateEmpleadoRequest.cs       [9 campos input]
│   │   └── EmpleadoResponse.cs            [10 campos output]
│   └── Extensions/
│       └── MappingExtensions.cs           [Conversiones DTO↔Entity]
└── DOC/
    ├── Punto1.md
    └── Punto2.md
```

---

## 🔄 Flujo de Datos

### POST /api/empleados
```
1. Cliente envía JSON con 9 campos (sin id, sin estado)
   ↓
2. CreateEmpleadoRequest (DTO de entrada) recibe datos
   ↓
3. MappingExtensions.ToEntity() convierte a Empleado:
   - Genera ID único (Guid.NewGuid())
   - Asigna Estado: ACTIVO (por defecto)
   ↓
4. EmpleadoService.RegistrarAsync() valida:
   - ¿Email existe? → 400 Bad Request
   - ¿NumeroEmpleado existe? → 400 Bad Request
   ↓
5. EmpleadoRepository.AñadirAsync() almacena en ConcurrentDictionary
   ↓
6. MappingExtensions.ToResponse() convierte a EmpleadoResponse
   ↓
7. Retorna 200 OK con 10 campos (incluyendo Id y Estado)
```

### GET /api/empleados/{id}
```
1. Cliente envía GET con {id} en la ruta
   ↓
2. EmpleadoService.BuscarPorIdAsync() busca en repositorio
   ↓
3. ¿Encontrado?
   → SÍ: ToResponse() + 200 OK
   → NO: 404 Not Found + "El empleado con id {id} no existe"
```

### Ruta no soportada
```
1. Cliente envía solicitud a ruta indefinida
   ↓
2. ASP.NET Core no encuentra endpoint coincidente
   ↓
3. Middleware 404 intercepta:
   - context.Response.StatusCode = 404
   - Retorna JSON: {"error": "Recurso no encontrado"}
```

---

## ✅ Validaciones Implementadas

### En EmpleadoService (Domain/Services/EmpleadoService.cs)
```csharp
// Validación de email
if (await _repository.ExisteEmailAsync(empleado.Email, cancellationToken))
{
    throw new EmpleadoDuplicadoException(
        "El email ya existe en el sistema",
        "email",
        empleado.Email
    );
}

// Validación de numeroEmpleado
if (await _repository.ExisteNumeroEmpleadoAsync(empleado.NumeroEmpleado, cancellationToken))
{
    throw new EmpleadoDuplicadoException(
        "El numero de empleado ya existe en el sistema",
        "numeroEmpleado",
        empleado.NumeroEmpleado
    );
}
```

### Middleware de excepciones (Program.cs líneas 29-62)
```csharp
// Captura EmpleadoDuplicadoException
if (exception is EmpleadoDuplicadoException duplicado)
{
    httpContext.Response.StatusCode = StatusCodes.Status400BadRequest;
    await httpContext.Response.WriteAsJsonAsync(new
    {
        error = duplicado.Message,
        campo = duplicado.Campo,
        valor = duplicado.Valor,
        timestamp = DateTime.UtcNow
    });
}
```

---

## 🧪 Casos de Prueba

### Test 1: POST exitoso
```bash
POST /api/empleados
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
**Esperado:** 200 OK + ID + Estado generados automáticamente

---

### Test 2: POST con email duplicado
```bash
POST /api/empleados
(enviar mismo email dos veces)
```
**Esperado:** 400 Bad Request + `{"error": "El email ya existe..."}`

---

### Test 3: POST con numeroEmpleado duplicado
```bash
POST /api/empleados
(enviar mismo numeroEmpleado dos veces)
```
**Esperado:** 400 Bad Request + `{"error": "El numero de empleado ya existe..."}`

---

### Test 4: GET exitoso
```bash
GET /api/empleados/{id}
(donde {id} es un ID válido)
```
**Esperado:** 200 OK + Datos del empleado

---

### Test 5: GET con ID inexistente
```bash
GET /api/empleados/invalid-id-xyz
```
**Esperado:** 404 Not Found + `{"error": "El empleado con id invalid-id-xyz no existe"}`

---

### Test 6: Ruta no soportada
```bash
GET /api/invalid
POST /empleados/extra
DELETE /api/empleados
```
**Esperado:** 404 Not Found + `{"error": "Recurso no encontrado"}`

---

## 🔧 Cambios Realizados

### En Program.cs
1. ✅ Líneas 64-78: Agregado middleware para capturar rutas 404
2. ✅ Línea 129: Cambio de `Results.Created()` a `Results.Ok()` para POST
3. ✅ Línea 159: Mensaje exacto: `"El empleado con id {id} no existe"`

### En RegistroService.csproj
1. ✅ Actualizado TargetFramework de net10.0 a net8.0 (compatibilidad)
2. ✅ Actualizado Microsoft.AspNetCore.OpenApi de 10.0.10 a 8.0.0

---

## 📝 Notas Técnicas

### HTTP Status Codes utilizados
| Código | Significado | Cuándo se usa |
|--------|-------------|---------------|
| 200 | OK | POST exitoso, GET encontrado |
| 400 | Bad Request | Validación fallida (email/numeroEmpleado duplicados) |
| 404 | Not Found | GET con ID inexistente, ruta no soportada |
| 500 | Internal Server Error | Error inesperado (middleware genérico) |

### Principios de diseño
1. **RESTful:** Endpoints siguen convención REST
2. **Separation of Concerns:** DTOs desacoplan API de dominio
3. **Exception Handling:** Middleware centralizado para excepciones
4. **Thread-safe:** ConcurrentDictionary para datos en memoria
5. **Dependency Injection:** Servicios inyectados en endpoints

---

## 🚀 Cómo ejecutar

1. **Compilar en Rider:** Build → Build Solution
2. **Ejecutar:** Run → Run 'RegistroService'
3. **Puerto predeterminado:** `http://localhost:5080`
4. **Documentación OpenAPI:** `http://localhost:5080/openapi/v1.json`

---

## ✨ Resumen

| Aspecto | Detalle |
|--------|---------|
| **POST /api/empleados** | 200 OK + Email/numeroEmpleado únicos |
| **GET /api/empleados/{id}** | 200 OK o 404 con mensaje exacto |
| **Rutas no soportadas** | 404 Not Found + "Recurso no encontrado" |
| **Validaciones** | Middleware de excepciones + lógica en servicio |
| **Puntuación esperada** | 2.0 pts (Criterios 1 y 2) |

---

**Documentado por:** Copilot  
**Versión:** 1.0  
**Última actualización:** 2026-08-05
