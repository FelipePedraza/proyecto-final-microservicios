# Sistema de Gestión de Empleados - Reto 3

Sistema de onboarding de empleados compuesto por microservicios independientes. En el Reto 3 se incorpora un **API Gateway como único punto de entrada** y un **Circuit Breaker** en la comunicación síncrona de RegistroService hacia DepartamentosService.

## Arquitectura

| Componente | Tecnología | Responsabilidad | Acceso desde el host |
|---|---|---|---|
| `gateway-service` | Java 21, Spring Cloud Gateway | Punto de entrada, enrutamiento, timeout y respuesta 503 controlada | `http://localhost:8088` |
| `registro-service` | ASP.NET Core 10 | Registro y consulta de empleados; consume DepartamentosService | No publicado; puerto interno `8080` |
| `departamentos-service` | Python 3.11, FastAPI | Registro y consulta de departamentos | No publicado; puerto interno `8081` |
| `notificaciones-service` | Node.js 20, Express | Consume eventos y consulta el historial de notificaciones | No publicado; puerto interno `8082` |
| `vacaciones-service` | Java 21, Spring Boot | Programa y cancela períodos; replica empleados por eventos | No publicado; puerto interno `8085` |
| `registro-db` | PostgreSQL 17 | Persistencia exclusiva de empleados | No publicado |
| `departamentos-db` | PostgreSQL 17 | Persistencia exclusiva de departamentos | No publicado |

Todos los contenedores pertenecen a la red privada `microservicios-network`. Las bases de datos y los servicios de negocio se declaran con `expose:`; solamente el Gateway usa `ports:`.

```text
Cliente HTTP
     |
     | http://localhost:8088
     v
API Gateway
     |------------------------------|
     v                              v
RegistroService                DepartamentosService
     |                              |
     v                              v
registro-db                    departamentos-db
     |
     +--- REST + Circuit Breaker ---> DepartamentosService

API Gateway ---> NotificacionesService
```

## URL base única

Desde el Reto 3, toda interacción externa debe usar:

```text
http://localhost:8088
```

Los puertos `8080`, `8081` y `8082` son internos de Docker. No deben utilizarse desde
Postman, Bruno, scripts de prueba ni clientes externos.

## API Gateway

### Tecnología elegida

Se eligió **Spring Cloud Gateway** porque el Gateway es un microservicio de aplicación y no solamente un proxy declarativo. Esta opción permite mantener el enrutamiento en código/configuración versionada y habilita las necesidades previstas para los siguientes retos: autenticación JWT, autorización, propagación de identidad, filtros transversales, métricas y composición de respuestas.

El Gateway no contiene reglas del dominio. Su responsabilidad se limita a enrutar y aplicar preocupaciones transversales.

### Tabla de rutas

| Ruta externa | Servicio interno | Uso |
|---|---|---|
| `GET /health` | Gateway | Salud propia del punto de entrada |
| `/empleados/**` | `http://registro-service:8080` | Registrar o consultar empleados |
| `/departamentos/**` | `http://departamentos-service:8081` | Registrar, listar o consultar departamentos |
| `/notificaciones/**` | `http://notificaciones-service:8082` | Consultar el historial de notificaciones |
| `/vacaciones/**` | `http://vacaciones-service:8085` | Programar, consultar y cancelar vacaciones |

El Gateway conserva la ruta, el cuerpo, las cabeceras y el código de estado producido por el servicio destino. Si el destino no responde, devuelve `503 Service Unavailable` con un cuerpo JSON descriptivo.

Ejemplo de respuesta de indisponibilidad:

```json
{
  "error": "service_unavailable",
  "message": "El microservicio destino no está disponible",
  "service": "departamentos-service",
  "path": "/fallback/departamentos-service"
}
```

## Puesta en marcha

### Requisitos

- Docker Desktop con Docker Compose.
- Para ejecutar pruebas de RegistroService fuera de Docker: .NET 10 SDK.

### Encender el sistema

Ejecutar desde la raíz del repositorio:

```bash
cd /Users/forest/Documents/Universidad/Semestre-9/Microservicios
docker compose up --build -d
docker compose ps
```

Antes de iniciar la demostración, `registro-service`, `departamentos-service` y `gateway-service` deben aparecer como `healthy`.

```bash
curl -i http://localhost:8088/health
curl -i http://localhost:8088/departamentos
```

### Apagar sin borrar datos

```bash
docker compose down
```

No usar `docker compose down --volumes` durante la demostración: esa variante elimina los datos locales y obliga a recrear las bases.

## Estructura de esquemas SQL

Para mantener una convención uniforme, todos los scripts iniciales de base de datos se almacenan bajo `database/` y se organizan por servicio:

```text
database/
  registro/001-schema.sql
  departamentos/001-schema.sql
  notificaciones/001-schema.sql
  perfiles/001-schema.sql
```

Esto mantiene el mismo patrón para todas las bases y permite que PostgreSQL ejecute los scripts desde `/docker-entrypoint-initdb.d` cuando se crea el volumen por primera vez.

## Ejemplos por medio del Gateway

### Crear un departamento

```bash
curl -i -X POST http://localhost:8088/departamentos \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "IT",
    "name": "Tecnología",
    "description": "Departamento de tecnología"
  }'
```

### Registrar un empleado

```bash
curl -i -X POST http://localhost:8088/empleados \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "E001",
    "nombre": "Juan",
    "apellido": "Pérez",
    "email": "juan.perez@example.com",
    "numeroEmpleado": "EMP-001",
    "cargo": "Desarrollador",
    "area": "Tecnología",
    "departamentoId": "IT",
    "fechaIngreso": "2026-09-20"
  }'
```

Con DepartamentosService disponible, la respuesta debe ser `201 Created` con `"estado": "ACTIVO"`.

### Consultar un empleado

```bash
curl -i http://localhost:8088/empleados/E001
```

> RegistroService actualmente implementa `POST /empleados` y `GET /empleados/{id}`. El listado `GET /empleados` todavía no está implementado.

### Consultar notificaciones

```bash
curl -i http://localhost:8088/notificaciones
curl -i http://localhost:8088/notificaciones/E001
```

## Circuit Breaker

RegistroService protege con **Polly** la llamada síncrona hacia DepartamentosService. El estado del circuito se comparte entre las solicitudes porque la política se registra como singleton.

### Parámetros

| Parámetro | Valor | Configuración |
|---|---:|---|
| Fallos consecutivos antes de abrir | 3 | `CB_FALLOS_CONSECUTIVOS` |
| Timeout de cada intento | 5 segundos | `CB_TIMEOUT_LLAMADA` |
| Tiempo en estado OPEN | 30 segundos | `CB_DURACION_CIRCUITO_ABIERTO` |
| Reintentos máximos | 3 intentos totales | Código de `DepartamentoClient` |
| Backoff | 1 s, 2 s | `EsperaBaseReintento` |

Los valores del umbral y de la duración se validan durante el arranque. El servicio no inicia si el umbral queda fuera de 3-5 fallos o la duración OPEN fuera de 30-60 segundos.

### Estados

- `CLOSED`: las llamadas llegan a DepartamentosService y los fallos se contabilizan.
- `OPEN`: las llamadas son rechazadas inmediatamente sin tocar la red y se ejecuta el fallback.
- `HALF_OPEN`: transcurridos 30 segundos se permite una llamada de prueba; un éxito cierra el circuito y un fallo vuelve a abrirlo.

El estado puede consultarse desde la red interna:

```bash
docker compose exec -T registro-service \
  curl -s http://localhost:8080/health/circuit-breaker
```

También se registran las transiciones:

```bash
docker compose logs --since=15m registro-service | \
  grep 'Circuito de departamentos'
```

### Fallback elegido

Se prioriza la **disponibilidad**: cuando DepartamentosService no está disponible o el circuito está abierto, el empleado se registra con estado `PENDIENTE_VALIDACION` y la operación devuelve `201 Created`. No se inventa un departamento por defecto.

Esta decisión permite que RR. HH. continúe trabajando durante una caída, a cambio de consistencia temporal. Los errores propios del dominio, como un departamento que responde `404`, no activan el fallback: se devuelven como `400 Bad Request`.

### Reconciliación de pendientes

El flujo de reconciliación definido es:

1. Conservar el empleado y su `departamentoId` original con estado `PENDIENTE_VALIDACION`.
2. Cuando DepartamentosService se recupere, un proceso idempotente vuelve a consultar ese departamento.
3. Si el departamento existe, el empleado pasa a `ACTIVO` y se registra la fecha de validación.
4. Si no existe, el empleado permanece pendiente y se genera una tarea para corrección por RR. HH.; nunca se asigna un departamento ficticio.
5. Los reintentos de reconciliación deben ser seguros e idempotentes para no duplicar empleados ni tareas.

El Circuit Breaker y la persistencia de `PENDIENTE_VALIDACION` ya están implementados. El proceso automático de reconciliación todavía no está implementado; queda identificado como trabajo necesario para cerrar completamente esta estrategia, idealmente mediante mensajería en el Reto 4.

## Demostración del Reto 3

### 1. Punto de entrada único

```bash
# Funciona por el Gateway
curl -i http://localhost:8088/departamentos
curl -i http://localhost:8088/empleados/E001

# Debe fallar desde el host
curl --max-time 3 -i http://localhost:8080/health
curl --max-time 3 -i http://localhost:8081/health
```

### 2. Error controlado del Gateway

```bash
docker compose stop departamentos-service
curl -i --max-time 10 http://localhost:8088/departamentos
```

La respuesta esperada es `503` con JSON descriptivo.

### 3. Apertura y fallback

Con DepartamentosService detenido, enviar registros de empleados con identificadores diferentes y medir cada solicitud:

```bash
for i in 1 2 3 4 5 6; do
  curl -sS --max-time 12 \
    -o "/tmp/reto3-respuesta-$i.json" \
    -w "peticion=$i http=%{http_code} tiempo=%{time_total}s\n" \
    -X POST http://localhost:8088/empleados \
    -H 'Content-Type: application/json' \
    -d "{
      \"id\": \"R3-CAIDA-$i\",
      \"nombre\": \"Prueba\",
      \"apellido\": \"Circuito$i\",
      \"email\": \"reto3.caida.$i@example.com\",
      \"numeroEmpleado\": \"R3-CAIDA-$i\",
      \"cargo\": \"Tester\",
      \"area\": \"Tecnología\",
      \"departamentoId\": \"IT\",
      \"fechaIngreso\": \"2026-09-20\"
    }"
  cat "/tmp/reto3-respuesta-$i.json"
  echo
done
```

El cambio de solicitudes lentas a respuestas casi inmediatas demuestra que el circuito pasó de `CLOSED` a `OPEN`. Con el circuito abierto, el cuerpo debe contener `"estado": "PENDIENTE_VALIDACION"`.

La configuración actual merece atención durante la prueba: el Gateway tiene un límite cercano a 6 segundos, mientras que una secuencia completa de reintentos de RegistroService puede durar más. Las primeras solicitudes podrían recibir `503` del Gateway antes del fallback; una vez abierto el circuito, el fallback sí debe responder inmediatamente. Si ocurre, debe corregirse la coordinación de timeouts antes de presentar la evidencia definitiva.

### 4. Recuperación automática

```bash
docker compose start departamentos-service
docker compose ps
sleep 35

curl -i -X POST http://localhost:8088/empleados \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "R3-RECUPERADO-001",
    "nombre": "Empleado",
    "apellido": "Recuperado",
    "email": "reto3.recuperado.001@example.com",
    "numeroEmpleado": "R3-RECUPERADO-001",
    "cargo": "Tester",
    "area": "Tecnología",
    "departamentoId": "NO-EXISTE",
    "fechaIngreso": "2026-09-20"
  }'
```

La respuesta esperada es `400 Bad Request`. Ese resultado demuestra que RegistroService volvió a consultar a DepartamentosService; un fallback habría producido `201` y `PENDIENTE_VALIDACION`.

## Evidencias y capturas

Guardar las capturas en [`docs/evidencias/reto3/`](docs/evidencias/reto3/) usando los siguientes nombres. No sustituirlas por resultados “esperados”: cada archivo debe mostrar una ejecución real.

| Archivo | Qué debe verse |
|---|---|
| `01-compose-healthy.png` | `docker compose ps`, todos los servicios sanos y solamente `8088` publicado |
| `02-acceso-directo-rechazado.png` | Fallo al consultar `localhost:8080` o `localhost:8081` |
| `03-recurso-por-gateway.png` | El mismo recurso funcionando mediante `localhost:8088` |
| `04-gateway-503-json.png` | Departamentos detenido y respuesta 503 JSON del Gateway |
| `05-circuito-closed.png` | Endpoint interno mostrando `CLOSED` |
| `06-salto-tiempos-open.png` | Tiempos de las solicitudes y salto a respuestas inmediatas |
| `07-circuito-open.png` | Estado `OPEN` y fallback `PENDIENTE_VALIDACION` |
| `08-transiciones-logs.png` | Logs `CLOSED -> OPEN`, `OPEN -> HALF_OPEN` y `HALF_OPEN -> CLOSED` |
| `09-recuperacion-400.png` | Respuesta 400 con `NO-EXISTE` después de restaurar Departamentos |
| `10-circuito-closed-final.png` | Estado final `CLOSED`, sin reiniciar RegistroService |

El índice de evidencias contiene una lista de verificación y el formato para enlazar cada imagen desde Markdown.

## Pruebas automatizadas

### RegistroService

```bash
cd Reto-1/RegistroService
dotnet test RegistroService.sln
```

Última comprobación local: **41 pruebas superadas**, incluyendo apertura, rechazo sin acceso a red, `HALF_OPEN`, recuperación, tratamiento de `404` y fallback.

### DepartamentosService

```bash
cd departamentos-serviceReto2
python3 -m pip install -r requirements-dev.txt
python3 -m pytest -q
```

### GatewayService

El Gateway todavía no cuenta con pruebas automatizadas propias. Deben agregarse pruebas para enrutamiento, propagación de cuerpo/cabeceras/status, `/health` y fallback 503.

## Limitaciones conocidas

- `GET /empleados` no está implementado; solo existen `POST /empleados` y `GET /empleados/{id}`.
- El proceso automático de reconciliación de empleados pendientes está diseñado, pero no implementado.
- El endpoint del estado del Circuit Breaker solo es accesible desde la red interna o mediante `docker compose exec`.
- Falta verificar y ajustar la coordinación entre el timeout del Gateway y la duración total de reintentos de RegistroService.
- El estado del Circuit Breaker vive en memoria de cada instancia de RegistroService.

## Estado del proyecto

| Reto | Estado | Alcance |
|---|---|---|
| Reto 1 | Completado | Registro y consulta individual de empleados |
| Reto 2 | Completado | Persistencia y comunicación REST entre servicios |
| Reto 3 | En progreso | Implementación lista |
### Justificaci�n de la Elecci�n del Message Broker

Para implementar la comunicaci�n asincr�nica y orientada a eventos, se investigaron tres opciones principales: **RabbitMQ**, **Apache Kafka** y **Redis Streams**. Se seleccion� **RabbitMQ** como la tecnolog�a id�nea para este proyecto por las siguientes razones:

1. **Retenci�n vs. Mensajer�a Pura:** A diferencia de **Kafka**, que est� dise�ado para retener un hist�rico de eventos de forma persistente (streaming y data pipelines), nuestro caso de uso requiere mensajer�a transaccional y r�pida. Una vez que los microservicios reaccionan a la creaci�n o retiro de un empleado, el evento ya no necesita persistir en el broker. Kafka habr�a introducido una complejidad innecesaria.
2. **Patr�n Fan-out Nativo:** El reto exige que un solo evento dispare m�ltiples acciones en distintos microservicios. RabbitMQ, mediante su protocolo AMQP y sus "Exchanges", maneja el enrutamiento *Fan-out* de forma nativa.
3. **Visibilidad y Depuraci�n:** RabbitMQ incluye una interfaz gr�fica (Management UI). Esto permite visualizar en tiempo real los exchanges, colas y mensajes, facilitando las pruebas.
4. **Est�ndar de la Industria:** RabbitMQ es ampliamente utilizado para arquitecturas orientadas a eventos en microservicios.
### Perfiles Service (Reto 4)

| Servicio | Lenguaje | Base de datos |
|---|---|---|
| `perfiles-service` | Go 1.22 | PostgreSQL 17 |

Go aporta un binario estático pequeño y concurrencia nativa para el consumidor RabbitMQ; PostgreSQL garantiza restricciones `UNIQUE`, transacciones y deduplicación durable. El servicio consume `empleado.creado`, `empleado.actualizado` y `empleado.retirado` del Catálogo de Eventos publicado en `empleados_exchange`.

Para probarlo: ejecutar `docker compose up --build`, crear un empleado por `http://localhost:8088/empleados`, consultar `http://localhost:8088/perfiles/{empleadoId}` y actualizar los campos propios mediante `PUT`. Publicar dos veces el mismo envelope en RabbitMQ deja una sola fila: el log del segundo consumo muestra `duplicate event`. Un retiro conserva el perfil y marca `archivado=true`; los datos sobreviven al reinicio por el volumen `perfiles-data`.

### Vacaciones Service (Reto 4)

| Servicio | Lenguaje / framework | Base de datos |
|---|---|---|
| `vacaciones-service` | Java 21 / Spring Boot | PostgreSQL 17 |

`vacaciones-service` usa Java y PostgreSQL porque Spring ofrece validación, transacciones y publicación AMQP integradas, mientras que PostgreSQL permite expresar la regla crítica de negocio con una restricción `EXCLUDE USING gist`: incluso dos solicitudes concurrentes no pueden crear períodos solapados para el mismo empleado. La API está disponible únicamente por `http://localhost:8088/vacaciones`; el puerto interno `8085` no se publica al host.

El servicio mantiene una réplica local de empleados a partir de `empleado.creado` y `empleado.retirado`, con deduplicación transaccional en `eventos_procesados`. Esta decisión prioriza disponibilidad y autonomía, coherente con la decisión de disponibilidad del Reto 3: programar vacaciones no depende de una llamada síncrona al servicio de empleados. La limitación es la consistencia eventual: empleados creados antes de que existiera la cola no aparecen en la réplica y existe una ventana entre la publicación y el consumo del evento.

Consume `empleado.creado` y `empleado.retirado` desde `empleados_exchange` y publica `vacaciones.programadas` en el mismo exchange después del commit de la transacción. La carga útil de `vacaciones.programadas` usada actualmente es la supuesta del reto (`vacacionId`, `empleadoId`, `fechaInicio`, `fechaFin`) y queda **pendiente de verificar contra el catálogo**. El consumidor tolera envelopes y datos PascalCase/camelCase.

Documentación interactiva:

```text
http://localhost:8088/vacaciones/docs
http://localhost:8088/vacaciones/api-docs
```

Ejemplo completo de validación y operación:

```bash
# Crear un período (el empleado debe existir y estar ACTIVO en la réplica)
curl -i -X POST http://localhost:8088/vacaciones \
  -H 'Content-Type: application/json' \
  -d '{"empleadoId":"E001","fechaInicio":"2026-10-01","fechaFin":"2026-10-10"}'

# 400: campos ausentes o fecha mal formada
curl -i -X POST http://localhost:8088/vacaciones \
  -H 'Content-Type: application/json' \
  -d '{"empleadoId":"E001","fechaInicio":"no-es-fecha","fechaFin":"2026-10-10"}'

# 400: fechaFin no posterior a fechaInicio
curl -i -X POST http://localhost:8088/vacaciones \
  -H 'Content-Type: application/json' \
  -d '{"empleadoId":"E001","fechaInicio":"2026-10-10","fechaFin":"2026-10-01"}'

# 400: fecha de inicio en el pasado
curl -i -X POST http://localhost:8088/vacaciones \
  -H 'Content-Type: application/json' \
  -d '{"empleadoId":"E001","fechaInicio":"2020-01-01","fechaFin":"2020-01-10"}'

# 400: empleado inexistente o retirado
curl -i -X POST http://localhost:8088/vacaciones \
  -H 'Content-Type: application/json' \
  -d '{"empleadoId":"NO-EXISTE","fechaInicio":"2026-10-01","fechaFin":"2026-10-10"}'

# Consultar por empleado y cancelar
curl -i 'http://localhost:8088/vacaciones?empleadoId=E001'
curl -i -X DELETE http://localhost:8088/vacaciones/V-2026-0001
```

Para comprobar deduplicación, publicar dos veces desde la UI de RabbitMQ el mismo envelope `empleado.creado` con el mismo `id`; la tabla `empleados_validos` debe conservar una sola fila y el log del segundo consumo debe indicar evento duplicado. Después de crear un período, `GET /notificaciones/E001` debe incluir una notificación de tipo `VACACIONES`.
