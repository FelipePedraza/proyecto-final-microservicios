# Sistema de Gestión de Empleados — Arquitectura de Microservicios

Proyecto final de la materia **Arquitectura de Microservicios**. Consiste en una serie de servicios independientes que colaboran para gestionar el ciclo de vida de los empleados de una organización.

## Visión general

El sistema está compuesto por microservicios especializados que se comunican entre sí para registrar, consultar y gestionar empleados. Cada servicio es independiente, tiene su propia base de datos (en este reto, en memoria) y expone una API REST.

## Módulos del proyecto

| Módulo | Descripción | Tecnología |
|--------|-------------|------------|
| **RegistroService** | Registro y consulta de empleados, persistencia PostgreSQL y consumo HTTP del servicio externo de Departamentos | ASP.NET Core 10, Minimal APIs |
| *(pendientes)* | *(se agregarán en retos siguientes)* | *(a definir)* |

## Prerrequisitos

- [.NET 10 SDK](https://dotnet.microsoft.com/download/dotnet/10.0)
- Docker 20.10+ (opcional, para contenerización)
- Git (para control de versiones)

## Cómo levantar el proyecto

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
# Registrar empleado
Invoke-RestMethod -Uri http://localhost:8080/empleados `
   -Method POST `
   -ContentType "application/json" `
   -Body '{"id":"E001","nombre":"Juan","apellido":"Perez","email":"juan@test.com","numeroEmpleado":"EMP001","cargo":"Dev","area":"Tech","departamentoId":"IT","fechaIngreso":"2026-02-10"}'

# probar duplicado
4. Probar duplicado (mismo email)
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

## Documentación detallada

Cada módulo tiene su propia documentación en su carpeta:

- [RegistroService README](Reto-1/RegistroService/README.md)
- [RegistroService DOC](Reto-1/RegistroService/RegistroService/DOC/)

## Estado del proyecto

| Reto | Estado | Descripción |
|------|--------|-------------|
| Reto 1 |  Completado | Registro y consulta de empleados |
| Reto 2 |  Pendiente | *(a definir)* |
| Reto 3 |  Pendiente | *(a definir)* |

## Licencia

Proyecto académico — UdeA
