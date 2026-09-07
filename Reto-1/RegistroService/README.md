# RegistroService — Reto 1 / Reto 2

Microservicio web en ASP.NET Core Minimal APIs para registrar empleados y consultarlos por identificador. La información se persiste en PostgreSQL y se valida el departamento mediante HTTP.

## Requisitos

- [.NET 10 SDK](https://dotnet.microsoft.com/download/dotnet/10.0)
- PostgreSQL 14 o superior
- Docker (opcional, para ejecutar en contenedor)
- Un servicio Departamentos disponible por HTTP (por defecto `http://localhost:8081/`)

## Estructura del proyecto

```
RegistroService/
├── RegistroService/                     # Proyecto API
│   ├── API/
│   │   ├── DTOs/
│   │   │   ├── CreateEmpleadoRequest.cs
│   │   │   └── EmpleadoResponse.cs
│   │   └── Extensions/
│   │       └── MappingExtensions.cs
│   ├── Domain/
│   │   ├── Entities/Empleado.cs
│   │   ├── Enums/EstadoEmpleado.cs
│   │   ├── Exceptions/
│   │   │   ├── DomainException.cs
│   │   │   └── EmpleadoDuplicadoException.cs
│   │   ├── Repositories/
│   │   │   ├── IEmpleadoRepository.cs
│   │   │   └── EmpleadoRepository.cs
│   │   └── Services/EmpleadoService.cs
│   ├── DOC/
│   │   ├── README.md
│   │   ├── Punto1.md
│   │   ├── Punto2.md
│   │   └── Punto3.md
│   ├── Program.cs
│   └── RegistroService.csproj
├── RegistroService.Tests/               # Pruebas automatizadas
│   ├── EmpleadosEndpointsTests.cs
│   ├── EmpleadoServiceTests.cs
│   ├── EmpleadoRepositoryTests.cs
│   └── RegistroService.Tests.csproj
├── Dockerfile
└── .dockerignore
```

## Ejecutar con Docker

Desde la carpeta `Reto-1/RegistroService/`:

```bash
docker build -t servidor-empleados .
docker run --rm -p 8080:8080 servidor-empleados
```

La API queda disponible en `http://localhost:8080`.

## Ejecutar sin Docker (dotnet run)

Desde la carpeta `Reto-1/RegistroService/`:

```bash
dotnet run --project RegistroService/RegistroService.csproj
```

**Puertos por defecto (launchSettings.json):**
- HTTP: `http://localhost:5080`
- HTTPS: `https://localhost:7217`

Swagger UI queda disponible en `/swagger` y el documento OpenAPI en `/swagger/v1/swagger.json`.

### Persistencia y configuración

La base de datos y la tabla se crean automáticamente al iniciar el servicio. La configuración está en `RegistroService/appsettings.json`:

```json
{
  "ConnectionStrings": {
    "Registro": "Host=localhost;Port=5432;Database=registro;Username=postgres;Password=postgres"
  },
  "Departamentos": {
    "BaseUrl": "http://localhost:8081/"
  }
}
```

Al registrar un empleado, el servicio consulta `GET {BaseUrl}/departamentos/{departamentoId}`. Un `404` rechaza el registro porque el departamento no existe; cualquier otro error del servicio remoto se propaga como error de comunicación.

## Probar la API

### Registrar empleado (POST /empleados)

```bash
curl -X POST http://localhost:8080/empleados \
  -H "Content-Type: application/json" \
  -d '{
    "id": "E001",
    "nombre": "Juan",
    "apellido": "Pérez",
    "email": "juan.perez@empresa.com",
    "numeroEmpleado": "EMP-2026-001",
    "cargo": "Desarrollador Senior",
    "area": "Tecnología",
    "departamentoId": "IT",
    "fechaIngreso": "2026-02-10"
  }'
```

**Respuesta 200 OK:**
```json
{
  "id": "E001",
  "nombre": "Juan",
  "apellido": "Pérez",
  "email": "juan.perez@empresa.com",
  "numeroEmpleado": "EMP-2026-001",
  "cargo": "Desarrollador Senior",
  "area": "Tecnología",
  "departamentoId": "IT",
  "fechaIngreso": "2026-02-10",
  "estado": "ACTIVO"
}
```

### Consultar empleado (GET /empleados/{id})

```bash
curl http://localhost:8080/empleados/E001
```

**Respuesta 200 OK:** misma estructura que el POST.

**Respuesta 404 (empleado no existe):**
```
El empleado con id NO-EXISTE no existe
```

## Contratos de API

| Método | Ruta | Descripción | Éxito | Error |
|--------|------|-------------|-------|-------|
| POST | `/empleados` | Registra un empleado | 200 OK + `EmpleadoResponse` | 400 Bad Request |
| GET | `/empleados/{id}` | Consulta por ID | 200 OK + `EmpleadoResponse` | 404 Not Found |
| * | Cualquier otra ruta | No soportado | — | 404 Not Found |

### Esquema de request (POST /empleados)

```json
{
  "id": "string (requerido)",
  "nombre": "string (requerido, no vacío)",
  "apellido": "string (requerido, no vacío)",
  "email": "string (requerido, único, case-insensitive, se almacena en minúsculas)",
  "numeroEmpleado": "string (requerido, único)",
  "cargo": "string (requerido, no vacío)",
  "area": "string (requerido, no vacío)",
  "departamentoId": "string (requerido, no vacío)",
  "fechaIngreso": "date (requerido, formato YYYY-MM-DD)"
}
```

### Esquema de response

```json
{
  "id": "string",
  "nombre": "string",
  "apellido": "string",
  "email": "string (minúsculas)",
  "numeroEmpleado": "string",
  "cargo": "string",
  "area": "string",
  "departamentoId": "string",
  "fechaIngreso": "date",
  "estado": "ACTIVO | EN_VACACIONES | RETIRADO"
}
```

### Códigos de error

| Código | Condición | Cuerpo de respuesta |
|--------|-----------|---------------------|
| 400 | Email duplicado | `Ya existe un empleado con email 'xxx'.` (texto plano) |
| 400 | Número de empleado duplicado | `Ya existe un empleado con numeroEmpleado 'xxx'.` (texto plano) |
| 400 | Campo vacío o inválido | `El valor es obligatorio.` (texto plano) |
| 404 | Empleado no encontrado | `El empleado con id {id} no existe` (texto plano) |
| 404 | Ruta no soportada | `Recurso no encontrado` (texto plano) |
| 500 | Error inesperado | `Ocurrió un error interno del servidor.` (texto plano) |

> Nota: Todas las respuestas de error tienen `Content-Type: text/plain; charset=utf-8`.

## Validaciones y reglas de negocio

1. **Campos requeridos:** Todos los campos del request son obligatorios. Si algún campo string está vacío o solo contiene espacios, retorna 400.
2. **Email único:** No se permite registrar dos empleados con el mismo email (case-insensitive).
3. **Número de empleado único:** No se permite registrar dos empleados con el mismo `numeroEmpleado` (case-sensitive).
4. **ID único:** No se permite registrar dos empleados con el mismo `id`.
5. **Estado por defecto:** Todo empleado registrado tiene estado `ACTIVO` automáticamente.
6. **Normalización de email:** El email se almacena y retorna en minúsculas.
7. **Trim automático:** Los campos string se recortan de espacios al registrar.
8. **Concurrencia:** El repositorio usa `lock` para garantizar operaciones atómicas. Si dos solicitudes concurrentes intentan registrar el mismo email o número de empleado, solo una succeederá.

## Persistencia

Los datos se almacenan en PostgreSQL. La tabla `Empleados` conserva los 10 campos del modelo canónico y tiene índices únicos para `Email` y `NumeroEmpleado`. El email se normaliza a minúsculas antes de persistirse y las restricciones se aplican en la base de datos, incluso ante solicitudes concurrentes.

## Pruebas automatizadas

```bash
dotnet test RegistroService.sln
```

La suite incluye:
- Pruebas de integración (endpoints HTTP): `EmpleadosEndpointsTests.cs`
- Pruebas unitarias (servicio): `EmpleadoServiceTests.cs`
- Pruebas unitarias (repositorio): `EmpleadoRepositoryTests.cs`

## Docker

### Construir imagen

```bash
cd Reto-1/RegistroService
docker build -t servidor-empleados .
```

### Ejecutar contenedor

```bash
docker run --rm -p 8080:8080 servidor-empleados
```

- La API queda en `http://localhost:8080`
- El contenedor se detiene con `Ctrl+C`
- `--rm` elimina el contenedor al detenerlo

### Probar desde el host

```bash
curl http://localhost:8080/empleados/E001
```

## Limitaciones conocidas

- Sin paginación ni filtros en consultas
- Sin endpoints de actualización, borrado o listado
- Sin autenticación ni autorización
- Sin límite de concurrencia configurado

## Autor

Persona 1, Persona 2, Persona 3  
**Fecha:** 2026-08-09
