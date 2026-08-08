# RegistroService — Reto 1

Servicio web en ASP.NET Core para registrar empleados y consultarlos por su identificador.
Los datos se conservan en memoria durante la ejecución de la aplicación.

## Ejecutar con Docker

Desde esta carpeta:

```bash
docker build -t servidor-empleados .
docker run --rm -p 8080:8080 servidor-empleados
```

La API queda disponible en `http://localhost:8080`.

## Probar la API

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

curl http://localhost:8080/empleados/E001
```

## Ejecutar sin Docker

Requiere el SDK de .NET 10:

```bash
dotnet run --project RegistroService/RegistroService.csproj
```

## Pruebas automatizadas

```bash
dotnet test RegistroService.sln
```

La suite verifica registro, consulta, duplicados, validaciones, rutas no soportadas y concurrencia.
