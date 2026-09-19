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

## 7. Circuit breaker y fallback (Reto 3)

Pruebas automatizadas de la máquina de estados (Polly real, sin mocks del breaker):

```powershell
dotnet test RegistroService.Tests/RegistroService.Tests.csproj --filter "DepartamentoClientTests|EmpleadoServiceTests|EmpleadosEndpointsTests"
```

Cubren: apertura tras 3 fallos consecutivos, rechazo sin tocar la red con el circuito OPEN, apertura a
mitad de los reintentos (umbral 5), reinicio de la cuenta con un éxito, el 404 que no cuenta como fallo,
HALF_OPEN → CLOSED si la llamada de prueba funciona, HALF_OPEN → OPEN si falla, y el fallback
`PENDIENTE_VALIDACION` (servicio y endpoint).

Verificación manual con Docker Compose:

```powershell
Invoke-RestMethod http://localhost:8080/health/circuit-breaker   # estado: CLOSED
docker compose stop departamentos-service
# POST /empleados (x3): 201 con "estado": "PENDIENTE_VALIDACION"; el circuito pasa a OPEN
Invoke-RestMethod http://localhost:8080/health/circuit-breaker   # estado: OPEN (respuesta inmediata)
docker compose start departamentos-service
# tras 30 s: HALF_OPEN; el siguiente POST /empleados devuelve "ACTIVO" y el circuito vuelve a CLOSED
docker compose logs registro-service | Select-String "Circuito de departamentos"
```

Transiciones esperadas en los logs:

```
Circuito de departamentos: CLOSED -> OPEN tras 3 fallos consecutivos. Rechazará llamadas durante 30 s. ...
Circuito de departamentos: OPEN -> HALF_OPEN. Se permite una llamada de prueba.
Circuito de departamentos: HALF_OPEN -> CLOSED. El servicio respondió, se reanuda el tráfico normal.
```
