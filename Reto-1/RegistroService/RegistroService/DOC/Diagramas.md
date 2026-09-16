# 📊 Diagramas y Arquitectura - Punto 1

## 1. Arquitectura en Capas

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                   │
│                       PRESENTATION LAYER                         │
│                      (Capa de Presentación)                      │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  HTTP Endpoints (Minimal APIs)                              ││
│  │  ├─ POST   /empleados (CreateEmpleadoRequest)          ││
│  │  └─ GET    /empleados/{id}                             ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  DTOs (Data Transfer Objects)                               ││
│  │  ├─ CreateEmpleadoRequest (entrada)                        ││
│  │  ├─ EmpleadoResponse (salida)                              ││
│  │  └─ MappingExtensions (conversión)                         ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Middleware                                                 ││
│  │  └─ Exception Handler (Convierte excepciones a HTTP)       ││
│  └─────────────────────────────────────────────────────────────┘│
│                              │                                    │
│                              ▼                                    │
│────────────────────────────────────────────────────────────────────│
│                                                                   │
│                      DOMAIN LAYER                                │
│                  (Capa de Lógica de Negocio)                     │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Services                                                   ││
│  │  └─ EmpleadoService                                         ││
│  │     ├─ RegistrarAsync()                                    ││
│  │     │  ├─ Valida: Email único                             ││
│  │     │  ├─ Valida: NumeroEmpleado único                    ││
│  │     │  └─ Lanza: EmpleadoDuplicadoException               ││
│  │     └─ BuscarPorIdAsync()                                  ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Entities                                                   ││
│  │  └─ Empleado (10 campos)                                   ││
│  │     ├─ Id, Nombre, Apellido, Email                         ││
│  │     ├─ NumeroEmpleado, Cargo, Area                         ││
│  │     ├─ DepartamentoId, FechaIngreso                        ││
│  │     └─ Estado (EstadoEmpleado enum)                        ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Exceptions                                                 ││
│  │  ├─ DomainException (base)                                 ││
│  │  └─ EmpleadoDuplicadoException                             ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Repository Interface                                       ││
│  │  └─ IEmpleadoRepository                                    ││
│  │     ├─ ObtenerPorIdAsync()                                 ││
│  │     ├─ ExisteEmailAsync()                                  ││
│  │     ├─ ExisteNumeroEmpleadoAsync()                         ││
│  │     └─ RegistrarAsync()                                    ││
│  └─────────────────────────────────────────────────────────────┘│
│                              │                                    │
│                              ▼                                    │
│────────────────────────────────────────────────────────────────────│
│                                                                   │
│                      INFRASTRUCTURE LAYER                        │
│                    (Capa de Persistencia)                        │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  EmpleadoRepository (Implementación)                        ││
│  │  ├─ ConcurrentDictionary<string, Empleado>               ││
│  │  ├─ Thread-safe                                            ││
│  │  ├─ En memoria                                             ││
│  │  └─ Válida para desarrollo                                 ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Flujo de Registro de Empleado (POST /empleados)

```
Cliente HTTP
    │
    │ POST /empleados
    │ {CreateEmpleadoRequest}
    │
    ▼
┌─────────────────────────────────────┐
│   Program.cs                        │
│   Endpoint: MapPost()               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│   MappingExtensions                 │
│   Request.ToEntity()                │
│   (Conserva el ID recibido)    │
└─────────────────────────────────────┘
    │
    ▼ (Empleado object)
┌─────────────────────────────────────┐
│   EmpleadoService.RegistrarAsync()  │
└─────────────────────────────────────┘
    │
    ├─────────────────────────────────┐
    │                                 │
    ▼                                 │
┌──────────────────────────────┐      │
│ IEmpleadoRepository          │      │
│ .ExisteEmailAsync()          │      │
└──────────────────────────────┘      │
    │                                 │
    ├─ Email existe ──┐              │
    │                 │              │
    │                 ▼              │
    │         ┌──────────────────┐   │
    │         │ Lanza Exception  │   │
    │         │ Duplicado        │   │
    │         └──────────────────┘   │
    │                 │              │
    └─ Email OK ──┐   │              │
                  │   │              │
                  ▼   │              │
            ┌──────────────────────────────────┐
            │ IEmpleadoRepository              │
            │ .ExisteNumeroEmpleadoAsync()     │
            └──────────────────────────────────┘
                  │
                  ├─ NumeroEmpleado existe ──┐
                  │                          │
                  │                          ▼
                  │                  ┌──────────────────┐
                  │                  │ Lanza Exception  │
                  │                  │ Duplicado        │
                  │                  └──────────────────┘
                  │                          │
                  └─ NumeroEmpleado OK ─┐   │
                                        │   │
                                        ▼   │
                            ┌──────────────────────────┐
                            │ IEmpleadoRepository      │
                            │ .RegistrarAsync()        │
                            │ (Guarda en memoria)      │
                            └──────────────────────────┘
                                        │
                                        ▼
                            ┌──────────────────────────┐
                            │ MappingExtensions        │
                            │ Empleado.ToResponse()    │
                            └──────────────────────────┘
                                        │
                                        ▼
                            ┌──────────────────────────┐
                            │ Return 201 Created       │
                            │ {EmpleadoResponse}       │
                            └──────────────────────────┘
                                        │
                                        ▼
                                   Cliente
```

---

## 3. Flujo de Validación de Duplicados

### Caso 1: Email Duplicado

```
POST /empleados
{
  "email": "juan.perez@company.com",
  ...
}
    │
    ▼
EmpleadoService.RegistrarAsync()
    │
    ▼ Llamada:
_repository.ExisteEmailAsync("juan.perez@company.com")
    │
    ▼
EmpleadoRepository.ExisteEmailAsync()
    │
    ├─ Normalizar: juan.perez@company.com (ya en minúsculas)
    │
    └─ Buscar en _empleados.Values.Any(e => e.Email == "juan.perez@company.com")
        │
        ├─ Si encuentra ──→ return true
        │                       │
        │                       ▼
        │                   Throw EmpleadoDuplicadoException
        │                   ("email", "juan.perez@company.com")
        │                       │
        │                       ▼
        │                   Middleware catch exception
        │                       │
        │                       ▼
        │                   Return 400 Bad Request
        │                   {
        │                     "error": "Ya existe un empleado...",
        │                     "campo": "email",
        │                     "valor": "juan.perez@company.com",
        │                     "timestamp": "..."
        │                   }
        │
        └─ Si no encuentra → return false → Continuar validación
```

### Caso 2: NumeroEmpleado Duplicado

```
POST /empleados
{
  "numeroEmpleado": "EMP001",
  ...
}
    │
    ▼
EmpleadoService.RegistrarAsync()
    │
    ├─ ✓ Email válido (no existe)
    │
    └─ Llamada:
       _repository.ExisteNumeroEmpleadoAsync("EMP001")
           │
           ▼
       EmpleadoRepository.ExisteNumeroEmpleadoAsync()
           │
           ├─ Normalizar: EMP001 (trim)
           │
           └─ Buscar en _empleados.Values.Any(e => e.NumeroEmpleado == "EMP001")
               │
               ├─ Si encuentra ──→ return true
               │                       │
               │                       ▼
               │                   Throw EmpleadoDuplicadoException
               │                   ("numeroEmpleado", "EMP001")
               │                       │
               │                       ▼
               │                   Middleware catch exception
               │                       │
               │                       ▼
               │                   Return 400 Bad Request
               │
               └─ Si no encuentra → return false → Registrar
```

---

## 4. Flujo de Búsqueda por ID

```
Cliente HTTP
    │
    │ GET /empleados/550e8400-e29b-41d4-a716-446655440000
    │
    ▼
┌────────────────────────────────────────┐
│ Program.cs                             │
│ Endpoint: MapGet()                     │
└────────────────────────────────────────┘
    │
    ▼
┌────────────────────────────────────────┐
│ EmpleadoService.BuscarPorIdAsync()     │
│ (Valida que ID no sea null/empty)      │
└────────────────────────────────────────┘
    │
    ▼
┌────────────────────────────────────────┐
│ IEmpleadoRepository.ObtenerPorIdAsync()│
└────────────────────────────────────────┘
    │
    ▼
┌────────────────────────────────────────┐
│ EmpleadoRepository                     │
│ _empleados.TryGetValue(id)             │
└────────────────────────────────────────┘
    │
    ├─ Encontrado ──────────┐
    │                       │
    │                       ▼
    │                ┌──────────────────┐
    │                │ MappingExtensions│
    │                │ ToResponse()     │
    │                └──────────────────┘
    │                       │
    │                       ▼
    │                Return 201 Created
    │                {EmpleadoResponse}
    │
    └─ No encontrado ───┐
                        ▼
                  Return 404 Not Found
                  {"error": "Empleado..."}
```

---

## 5. Estructura de ConcurrentDictionary (Persistencia)

```
┌─ EmpleadoRepository
│
└─ _empleados: ConcurrentDictionary<string, Empleado>
    │
    ├─ Key: "550e8400-e29b-41d4-a716-446655440000"
    │  Value: Empleado {
    │    Id: "550e8400-e29b-41d4-a716-446655440000",
    │    Nombre: "Juan",
    │    Apellido: "Pérez",
    │    Email: "juan.perez@company.com",
    │    NumeroEmpleado: "EMP001",
    │    Cargo: "Ingeniero",
    │    Area: "Tecnología",
    │    DepartamentoId: "DEP-TI-001",
    │    FechaIngreso: 2024-01-15,
    │    Estado: EstadoEmpleado.Activo
    │  }
    │
    ├─ Key: "a1b2c3d4-e5f6-47a8-b9c0-d1e2f3a4b5c6"
    │  Value: Empleado { ... }
    │
    └─ Key: "..."
       Value: Empleado { ... }
```

---

## 6. Ciclo de Vida de Inyección de Dependencias

```
Configuración en Program.cs:
    │
    ├─ builder.Services.AddSingleton<IEmpleadoRepository, EmpleadoRepository>()
    │  │
    │  └─ Una sola instancia durante TODA la aplicación
    │     │
    │     └─ ConcurrentDictionary<string, Empleado>
    │        (todos los datos comparten la misma instancia)
    │
    └─ builder.Services.AddScoped<EmpleadoService>()
       │
       └─ Nueva instancia POR CADA REQUEST HTTP
          │
          └─ Inyecta la misma IEmpleadoRepository (singleton)
```

---

## 7. Mapeo de DTOs

```
CreateEmpleadoRequest (JSON entrada)
    │
    │ {
    │   "nombre": "Juan",
    │   "apellido": "Pérez",
    │   "email": "juan.perez@company.com",
    │   "numeroEmpleado": "EMP001",
    │   "cargo": "Ingeniero",
    │   "area": "Tecnología",
    │   "departamentoId": "DEP-TI-001",
    │   "fechaIngreso": "2024-01-15"
    │ }
    │
    ▼ MappingExtensions.ToEntity()
    │
    └─→ Empleado (Entidad de Dominio)
        │
        ├─ Id: request.Id  ← Proporcionado por el cliente
        ├─ Nombre: "Juan"
        ├─ Apellido: "Pérez"
        ├─ Email: "juan.perez@company.com"
        ├─ NumeroEmpleado: "EMP001"
        ├─ Cargo: "Ingeniero"
        ├─ Area: "Tecnología"
        ├─ DepartamentoId: "DEP-TI-001"
        ├─ FechaIngreso: DateOnly(2024, 1, 15)
        └─ Estado: EstadoEmpleado.Activo  ← Automático
            │
            ▼ EmpleadoService.RegistrarAsync()
            │ (Validaciones)
            │
            ▼ IEmpleadoRepository.RegistrarAsync()
            │ (Guardar)
            │
            ▼ MappingExtensions.ToResponse()
            │
            └─→ EmpleadoResponse (JSON salida)
                │
                └─ {
                     "id": "550e8400-e29b-41d4-a716-446655440000",
                     "nombre": "Juan",
                     "apellido": "Pérez",
                     "email": "juan.perez@company.com",
                     "numeroEmpleado": "EMP001",
                     "cargo": "Ingeniero",
                     "area": "Tecnología",
                     "departamentoId": "DEP-TI-001",
                     "fechaIngreso": "2024-01-15",
                     "estado": "Activo"
                   }
```

---

## 8. Matriz de Validaciones

| Validación | Ubicación | Condición | Respuesta | HTTP Status |
|------------|-----------|-----------|-----------|------------|
| Email duplicado | EmpleadoService | Ya existe en repositorio | EmpleadoDuplicadoException | 409 |
| NumeroEmpleado duplicado | EmpleadoService | Ya existe en repositorio | EmpleadoDuplicadoException | 409 |
| Empleado no encontrado | Program.cs:126 | ID no existe en repositorio | {"error": "Empleado..."} | 404 |
| Registro exitoso | Program.cs:110 | Todas las validaciones pasaron | EmpleadoResponse | 201 |
| Búsqueda exitosa | Program.cs:138 | Empleado encontrado | EmpleadoResponse | 200 |

---

**Diagrama final para sustentación:** ✅ Listo
