# Sistema de Gestión de Empleados — Arquitectura de Microservicios

Proyecto final de la materia **Arquitectura de Microservicios**. Consiste en una serie de servicios independientes que colaboran para gestionar el ciclo de vida de los empleados de una organización.

## Visión general

El sistema está compuesto por microservicios especializados que se comunican entre sí para registrar, consultar y gestionar empleados. Cada servicio es independiente, tiene su propia base de datos PostgreSQL y expone una API REST.

## Tolerancia a fallos y resiliencia

La integración entre RegistroService y DepartamentosService ahora contempla:

- timeout por request de 5 segundos para la llamada HTTP a Departamentos.
- reintentos exponenciales con backoff para errores transitorios (429, 408, 5xx).
- manejo de 404 como caso normal de negocio: el departamento no existe y debe devolverse un error de dominio.
- arranque ordenado con `depends_on` y health checks de Compose, de forma que RegistroService no inicia antes de que la base y el servicio de departamentos estén listos.

Para la validación de estos escenarios se mantienen pruebas de integración y de cliente HTTP que cubren la comunicación dependiente.

## Módulos del proyecto

| Módulo | Descripción | Tecnología |
|--------|-------------|------------|
| **RegistroService** | Registro y consulta de empleados, persistencia PostgreSQL y consumo HTTP del servicio externo de Departamentos | ASP.NET Core 10, Minimal APIs |
| **DepartamentosService** | Registro y consulta de departamentos | Python 3.11, FastAPI, SQLAlchemy |
| **GatewayService** | Enrutamiento, salud y respuesta de indisponibilidad | Java 21, Spring Cloud Gateway |

## Prerrequisitos

- [.NET 10 SDK](https://dotnet.microsoft.com/download/dotnet/10.0)
- Docker con Docker Compose
- Git (para control de versiones)

## Cómo levantar el proyecto completo con Docker Compose

Desde la carpeta `Reto-2`:

```bash
# Opcional: personalizar puertos y credenciales locales.
cp .env.example .env

# Construir las APIs y levantar APIs + bases de datos.
docker compose up --build -d

# Ver el estado y esperar a que todos aparezcan healthy.
docker compose ps

# Consultar los logs.
docker compose logs -f
```

Servicios publicados en el equipo:

- RegistroService: `http://localhost:8080` (Swagger en `/swagger`)
- DepartamentosService: `http://localhost:8081` (Swagger en `/docs`)
- GatewayService: `http://localhost:8088` (entrada para `/empleados/*` y `/departamentos/*`)

El Gateway conserva la ruta, los headers, el cuerpo y el código de estado de las
respuestas de los servicios destino. Su endpoint propio es `GET /health`. Si un
servicio destino no responde, retorna `503 Service Unavailable` con un JSON
descriptivo.

Las bases de datos no publican puertos al anfitrión: solamente son accesibles por
los servicios dentro de la red privada `microservicios-network`. Cada
microservicio tiene su propia base y su propio volumen; ningún servicio consulta
directamente la base del otro.

### Creación reproducible de esquemas

PostgreSQL ejecuta automáticamente los archivos de
`/docker-entrypoint-initdb.d` la primera vez que crea cada volumen:

- `database/registro/001-schema.sql` crea el esquema de empleados.
- `departamentos-serviceReto2/init.sql` crea el esquema de departamentos.

Para recrear desde cero las dos bases y volver a ejecutar los scripts (esto borra
los datos locales):

```bash
docker compose down --volumes
docker compose up --build -d
```

Para detener los contenedores sin borrar los datos:

```bash
docker compose down
```

Compose espera a que cada PostgreSQL esté saludable antes de iniciar su API. A
su vez, RegistroService espera a que DepartamentosService esté saludable porque
lo consume mediante HTTP usando el nombre DNS interno
`http://departamentos-service:8081/`.

## Cómo levantar un servicio sin Compose

### 1. Clonar el repositorio

```bash
git clone <url-del-repositorio>
cd proyecto-final-microservicios
```

### 2. Levantar RegistroService (sin Docker)

```bash
cd Reto-1/RegistroService
dotnet run --project RegistroService/RegistroService.csproj
```

La API queda disponible en `http://localhost:5080` (HTTP) o `https://localhost:7217` (HTTPS). Swagger UI está disponible en `/swagger`.

### 3. Levantar RegistroService (con Docker)

```bash
cd Reto-1/RegistroService
docker build -t servidor-empleados .
docker run --rm -p 8080:8080 servidor-empleados
```

La API queda disponible en `http://localhost:8080`.

### 4. Ejecutar pruebas

```bash
cd Reto-1/RegistroService
dotnet test RegistroService.sln
```

## Pruebas rápidas de la API

```bash
# Verificar salud
Invoke-RestMethod -Uri http://localhost:8080/health

# Registrar empleado
Invoke-RestMethod -Uri http://localhost:8080/empleados `
   -Method POST `
   -ContentType "application/json" `
   -Body '{"id":"E001","nombre":"Juan","apellido":"Perez","email":"juan@test.com","numeroEmpleado":"EMP001","cargo":"Dev","area":"Tech","departamentoId":"IT","fechaIngreso":"2026-02-10"}'

# Probar duplicado (mismo email)
try {
    $response = Invoke-WebRequest -Uri http://localhost:8080/empleados `
      -Method POST `
      -ContentType "application/json" `
      -Body '{"id":"E002","nombre":"Ana","apellido":"Lopez","email":"juan@test.com","numeroEmpleado":"EMP002","cargo":"QA","area":"Tech","departamentoId":"IT","fechaIngreso":"2026-02-10"}'
    $response.Content
} catch {
    $_.Exception.Response.StatusCode
    $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    $reader.ReadToEnd()
}

# Consultar empleado
Invoke-RestMethod -Uri http://localhost:8080/empleados/E001
```

## Pruebas solicitadas por el profesor (Reto 2)

> Antes de ejecutar, asegúrate de que los servicios estén levantados y healthys:
> `docker compose up --build -d` y luego `docker compose ps` (todo debe aparecer en `healthy`).

### 1. Levantar los servicios

```powershell
docker compose up --build -d
docker compose ps
```

### 2. Crear un departamento válido

```powershell
$jsonDept = '{"id":"IT","name":"Tecnología","description":"Departamento de TI"}'
$bytesDept = [System.Text.Encoding]::UTF8.GetBytes($jsonDept)

Invoke-RestMethod -Uri "http://localhost:8081/departamentos" `
    -Method Post `
    -ContentType "application/json; charset=utf-8" `
    -Body $bytesDept
```

### 3. Crear un empleado asociado al departamento

```powershell
$jsonEmp = '{
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
$bytesEmp = [System.Text.Encoding]::UTF8.GetBytes($jsonEmp)

Invoke-RestMethod -Uri "http://localhost:8080/empleados" `
    -Method Post `
    -ContentType "application/json; charset=utf-8" `
    -Body $bytesEmp
```

### 4. Verificar que el empleado existe

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/empleados/E001" -Method Get
```

### 5. Validaciones (todas deben responder 400 Bad Request)

#### a) Email duplicado

```powershell
$jsonDupEmail = '{
  "id": "E002",
  "nombre": "Ana",
  "apellido": "Gómez",
  "email": "juan.perez@empresa.com",
  "numeroEmpleado": "EMP-2026-002",
  "cargo": "QA",
  "area": "Tecnología",
  "departamentoId": "IT",
  "fechaIngreso": "2026-02-11"
}'
$bytesDupEmail = [System.Text.Encoding]::UTF8.GetBytes($jsonDupEmail)

Invoke-RestMethod -Uri "http://localhost:8080/empleados" `
    -Method Post `
    -ContentType "application/json; charset=utf-8" `
    -Body $bytesDupEmail
```

#### b) Número de empleado duplicado

```powershell
$jsonDupNum = '{
  "id": "E003",
  "nombre": "Ana",
  "apellido": "Gómez",
  "email": "ana@empresa.com",
  "numeroEmpleado": "EMP-2026-001",
  "cargo": "QA",
  "area": "Tecnología",
  "departamentoId": "IT",
  "fechaIngreso": "2026-02-11"
}'
$bytesDupNum = [System.Text.Encoding]::UTF8.GetBytes($jsonDupNum)

Invoke-RestMethod -Uri "http://localhost:8080/empleados" `
    -Method Post `
    -ContentType "application/json; charset=utf-8" `
    -Body $bytesDupNum
```

#### c) Departamento inexistente

```powershell
$jsonBadDept = '{
  "id": "E004",
  "nombre": "Ana",
  "apellido": "Gómez",
  "email": "ana@empresa.com",
  "numeroEmpleado": "EMP-2026-004",
  "cargo": "QA",
  "area": "Ventas",
  "departamentoId": "NO-EXISTE",
  "fechaIngreso": "2026-02-11"
}'
$bytesBadDept = [System.Text.Encoding]::UTF8.GetBytes($jsonBadDept)

Invoke-RestMethod -Uri "http://localhost:8080/empleados" `
    -Method Post `
    -ContentType "application/json; charset=utf-8" `
    -Body $bytesBadDept
```

## Verificación de arranque ordenado y tolerancia a fallos

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f departamentos-service
curl http://localhost:8081/health
curl http://localhost:8080/health
```

La validación esperada es:

- `departamentos-db` aparece como `healthy`.
- `departamentos-service` aparece como `healthy`.
- `registro-service` queda activo solo después de que `departamentos-service` esté listo.
- el endpoint `/health` responde exitosamente en ambos servicios.

## Evidencias

Las verificaciones concretas quedan documentadas en [EVIDENCIAS.md](EVIDENCIAS.md). El flujo recomendado es:

1. arranque con `docker compose up --build -d`;
2. `docker compose ps` para verificar `healthy`;
3. `curl` o `Invoke-WebRequest` contra `/health`;
4. pruebas `dotnet test` para comprobar la resiliencia y la API.

## Documentación detallada

Cada módulo tiene su propia documentación en su carpeta:

- [RegistroService README](Reto-1/RegistroService/README.md)
- [RegistroService DOC](Reto-1/RegistroService/RegistroService/DOC/)

## Estado del proyecto

| Reto | Estado | Descripción |
|------|--------|-------------|
| Reto 1 |  Completado | Registro y consulta de empleados |
| Reto 2 |  Completado | Integración de Registro y Departamentos con bases PostgreSQL independientes |
| Reto 3 |  Pendiente | *(a definir)* |

## Licencia

Proyecto académico — UdeA
