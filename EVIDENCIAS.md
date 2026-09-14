# Evidencias de validación

## 1. Estado de Docker Compose

```powershell
docker compose ps
```

Resultado esperado:

- `departamentos-db` en estado `healthy`
- `departamentos-service` en estado `healthy`
- `registro-service` en estado `healthy` si el servicio ya fue arrancado por dependencias

## 2. Salud de cada servicio

```powershell
Invoke-WebRequest -Uri http://localhost:8081/health
Invoke-WebRequest -Uri http://localhost:8080/health
```

Comportamiento esperado:

```json
{"status":"healthy"}
```

## 3. Verificación de reintentos del cliente HTTP

Se añadió una prueba de cliente que simula una primera respuesta `503` y una segunda respuesta `200`.

```powershell
dotnet test RegistroService.Tests/RegistroService.Tests.csproj --filter DepartamentoClientTests
```

Se espera:

- `Exito` en la prueba
- 2 intentos en total

## 4. Pruebas de integración de la API

```powershell
dotnet test RegistroService.Tests/RegistroService.Tests.csproj
```

Se espera:

- todas las pruebas de `EmpleadosEndpointsTests` pasando
- validación de endpoints, mensajes de error de dominio y rutas no encontradas

## 5. Persistencia down vs down -v

Cuando se requiere limpiar la base local:

```powershell
docker compose down --volumes
docker compose up --build -d
```

Esto regenerará los esquemas iniciales desde:

- `database/registro/001-schema.sql`
- `departamentos-serviceReto2/init.sql`

## 6. Arranque ordenado

El arranque de Compose se asegura con:

- healthchecks de PostgreSQL
- `depends_on` con condición `service_healthy` para el servicio de departamentos
- `depends_on` con condición `service_healthy` para `registro-service`

Esto garantiza que RegistroService no intente consumir DepartamentosService antes de que esté listo.
