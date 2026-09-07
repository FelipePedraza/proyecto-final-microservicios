# Sistema de Gestión de Empleados — Arquitectura de Microservicios

Proyecto final de la materia **Arquitectura de Microservicios**. Consiste en una serie de servicios independientes que colaboran para gestionar el ciclo de vida de los empleados de una organización.

## Visión general

El sistema está compuesto por microservicios especializados que se comunican entre sí para registrar, consultar y gestionar empleados. Cada servicio es independiente, tiene su propia base de datos (en este reto, en memoria) y expone una API REST.

## Módulos del proyecto

| Módulo | Descripción | Tecnología |
|--------|-------------|------------|
| **RegistroService** | Registro y consulta de empleados, persistencia PostgreSQL y consulta HTTP de Departamentos | ASP.NET Core 10, Minimal APIs |
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

# Consultar empleado
curl http://localhost:8080/empleados/E001
```

## Documentación detallada

Cada módulo tiene su propia documentación en su carpeta:

- [RegistroService README](Reto-1/RegistroService/README.md)
- [RegistroService DOC](Reto-1/RegistroService/RegistroService/DOC/)

## Estado del proyecto

| Reto | Estado | Descripción |
|------|--------|-------------|
| Reto 1 | ✅ Completado | Registro y consulta de empleados |
| Reto 2 | ✅ Implementado en RegistroService | Persistencia PostgreSQL, unicidad en BD, consumo HTTP de Departamentos y Swagger |
| Reto 3 | ⏳ Pendiente | *(a definir)* |

## Licencia

Proyecto académico — UdeA
