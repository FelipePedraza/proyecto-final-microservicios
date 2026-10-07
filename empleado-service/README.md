# EmpleadoService — Reto 1 / Reto 2

Microservicio web en ASP.NET Core Minimal APIs para registrar empleados y consultarlos por identificador. La información se persiste en PostgreSQL y consume por HTTP el servicio externo de Departamentos.

## Requisitos

- [.NET 10 SDK](https://dotnet.microsoft.com/download/dotnet/10.0)
- PostgreSQL 14 o superior
- Docker (opcional, para ejecutar en contenedor)
- El servicio externo de Departamentos disponible por HTTP (por defecto `http://localhost:8081/`)

## Estructura del proyecto

```
empleado-service/
├── empleadoService/                     # Proyecto API
│   ├── api/
│   │   ├── dtos/
│   │   │   ├── CreateEmpleadoRequest.cs
│   │   │   ├── EmpleadoResponse.cs
│   │   │   └── ApiResponseContracts.cs
│   │   └── extensions/
│   │       └── MappingExtensions.cs
│   ├── domain/
│   │   ├── entities/Empleado.cs
│   │   ├── enums/EstadoEmpleado.cs
│   │   ├── exceptions/
│   │   │   ├── DomainException.cs
│   │   │   └── EmpleadoDuplicadoException.cs
│   │   ├── repositories/
│   │   │   ├── IEmpleadoRepository.cs
│   │   │   └── EmpleadoRepository.cs
│   │   └── services/EmpleadoService.cs
│   ├── docs/
│   │   ├── README.md
│   │   ├── Punto1.md
│   │   ├── Punto2.md
│   │   └── Punto3.md
│   ├── Program.cs
│   └── EmpleadoService.csproj
├── empleadoService.Tests/               # Pruebas automatizadas
│   ├── EmpleadosEndpointsTests.cs
│   ├── EmpleadoServiceTests.cs
│   ├── EmpleadoRepositoryTests.cs
│   └── EmpleadoService.Tests.csproj
├── Dockerfile
└── .dockerignore
```

## Ejecutar con Docker

Desde la carpeta `empleado-service/`:

```bash
docker run --rm -p 8080:8080 empleado-service
```

La API queda disponible en `http://localhost:8080`.

## Ejecutar sin Docker (dotnet run)

Desde la carpeta `empleado-service/`:

```bash
dotnet run --project empleadoService/EmpleadoService.csproj
```

**Puertos por defecto (launchSettings.json):**
- HTTP: `http://localhost:5080`
- HTTPS: `https://localhost:7217`

Swagger UI queda disponible en `/swagger` y el documento OpenAPI en `/swagger/v1/swagger.json`.
La especificación describe los endpoints de empleados y health, sus cuerpos JSON, códigos de
respuesta y parámetros. Las operaciones `/empleados` incluyen el esquema Bearer JWT que aplica
el Gateway para que su Swagger agregado pueda enviar el token. Es metadata de documentación:
EmpleadoService no valida JWT directamente. Los endpoints de health permanecen públicos en la
especificación.

### Persistencia y configuración

El esquema de la base de datos se crea mediante el script
`database/empleado/001-schema.sql` montado por Docker Compose. La configuración
está en `empleadoService/appsettings.json` y usa la clave de conexión
`EmpleadoService`.

```json
{
  "ConnectionStrings": {
    "EmpleadoService": "Host=localhost;Port=5432;Database=empleado_db;Username=postgres;Password=postgres"
  },
  "Departamentos": {
    "BaseUrl": "http://localhost:8081/"
  }
}
```

EmpleadoService no implementa ni almacena Departamentos. Al registrar un empleado, consume
`GET {BaseUrl}/departamentos/{departamentoId}` del microservicio externo. Un `404` rechaza el
registro porque el departamento no existe; si el servicio remoto no está disponible, se aplica el
fallback y el empleado queda con estado `PENDIENTE_VALIDACION`.

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

**Respuesta 201 Created:**

La respuesta incluye el encabezado `Location: /empleados/E001`.
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
```json
{
  "error": "El empleado con id NO-EXISTE no existe"
}
```

## Contratos de API

| Método | Ruta | Descripción | Respuestas |
|--------|------|-------------|-------------|
| GET | `/health` | Liveness de EmpleadoService | 200 `HealthResponse` |
| GET | `/health/ready` | Disponibilidad de base de datos | 200 `ReadinessResponse` / 503 no listo |
| GET | `/health/circuit-breaker` | Estado del circuito de DepartamentoService | 200 `CircuitBreakerHealthResponse` |
| POST | `/empleados` | Registra empleado; responde `Location` | 201 `EmpleadoResponse` / 400 / 409 / 503 / 500 |
| GET | `/empleados/{id}` | Consulta por ID | 200 `EmpleadoResponse` / 404 / 503 / 500 |
| PUT | `/empleados/{id}` | Actualiza empleado usando el cuerpo de creación | 200 `EmpleadoResponse` / 400 / 404 / 503 / 500 |
| GET | `/empleados?estado=RETIRADO&desde={fecha}&hasta={fecha}` | Consulta retirados con filtros de fecha opcionales | 200 lista de `RetiredEmpleadoResponse` / 400 / 503 / 500 |
| DELETE | `/empleados/{id}` | Baja lógica | 204 / 404 / 409 / 503 / 500 |
| * | Cualquier otra ruta | No soportado | 404 `ErrorResponse` |

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
  "estado": "ACTIVO | EN_VACACIONES | RETIRADO | PENDIENTE_VALIDACION"
}
```

### Códigos de error

| Código | Condición | Valor de `error` |
|--------|-----------|--------------------|
| 409 | Email duplicado | `Ya existe un empleado con email 'xxx'.` |
| 409 | Número de empleado duplicado | `Ya existe un empleado con numeroEmpleado 'xxx'.` |
| 409 | Empleado ya retirado | `El empleado con id {id} ya está retirado.` |
| 400 | Departamento inexistente | `El departamento con id xxx no existe.` |
| 400 | Campo vacío o inválido | `El valor es obligatorio.` |
| 404 | Empleado no encontrado | `El empleado con id {id} no existe` |
| 404 | Ruta no soportada | `Recurso no encontrado` |
| 500 | Error inesperado | `Ocurrió un error interno del servidor.` |

Las respuestas de error usan JSON con la propiedad `error`.

## Validaciones y reglas de negocio

1. **Campos requeridos:** Todos los campos del request son obligatorios. Si algún campo string está vacío o solo contiene espacios, retorna 400.
2. **Email único:** No se permite registrar dos empleados con el mismo email (case-insensitive).
3. **Número de empleado único:** No se permite registrar dos empleados con el mismo `numeroEmpleado` (case-sensitive).
4. **ID único:** No se permite registrar dos empleados con el mismo `id`.
5. **Estado por defecto:** Todo empleado registrado tiene estado `ACTIVO` automáticamente.
6. **Normalización de email:** El email se almacena y retorna en minúsculas.
7. **Trim automático:** Los campos string se recortan de espacios al registrar.
8. **Concurrencia:** Los índices únicos de PostgreSQL garantizan la unicidad entre solicitudes concurrentes; los conflictos se traducen a 409 Conflict.

## Persistencia

Los datos se almacenan en PostgreSQL. La tabla `Empleados` conserva los 10 campos del modelo canónico y tiene índices únicos para `Email` y `NumeroEmpleado`. El email se normaliza a minúsculas antes de persistirse y las restricciones se aplican en la base de datos, incluso ante solicitudes concurrentes.

## Pruebas automatizadas

Las pruebas unitarias de `EmpleadoService` sustituyen `IEmpleadoRepository`,
`IDepartamentoClient` y `IEventPublisher` por fakes; no requieren PostgreSQL,
Docker, RabbitMQ ni llamadas HTTP reales. Las pruebas de duplicados verifican la
propagación de `EmpleadoDuplicadoException` desde el repositorio falso. La
unicidad se aplica en `EmpleadoRepository.RegistrarAsync` (y en los índices
únicos de persistencia), no mediante una consulta de existencia en
`EmpleadoService`.

```bash
dotnet test EmpleadoService.sln
```

Para generar resultados TRX y cobertura Cobertura desde `empleado-service`:

```bash
dotnet test empleadoService.Tests\EmpleadoService.Tests.csproj --collect:"XPlat Code Coverage" --logger trx --settings empleadoService.Tests\EmpleadoService.Tests.runsettings
```

El logger TRX crea el resultado directamente en
`empleadoService.Tests\TestResults\<host>_<fecha>_net10.0.trx`. Coverlet crea
`empleadoService.Tests\TestResults\<GUID>\coverage.cobertura.xml`; `<GUID>` es
la carpeta única generada para esa cobertura. Ambos formatos son consumibles
por CI y usan las rutas estándar de `dotnet test`.
El archivo de configuración filtra la cobertura a `EmpleadoService.Domain.*`
para medir la lógica del dominio sin mezclar código de API e infraestructura.

La suite incluye:
- Pruebas de integración (endpoints HTTP): `EmpleadosEndpointsTests.cs`
- Pruebas unitarias (servicio con test doubles): `EmpleadoServiceTests.cs`
- Pruebas de repositorio con EF Core InMemory: `EmpleadoRepositoryTests.cs`

Una segunda retirada ahora se rechaza mediante `EmpleadoYaRetiradoException`.
La API la traduce a HTTP 409 Conflict; las pruebas unitarias verifican que la
fecha y los efectos externos no se repiten y una prueba de endpoint verifica el
código de respuesta.

## Docker

### Construir imagen

```bash
cd empleado-service
docker build -t empleado-service .
```

### Ejecutar contenedor

```bash
docker run --rm -p 8080:8080 empleado-service
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
