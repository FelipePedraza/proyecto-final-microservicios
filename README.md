# Sistema de Gestión de Empleados — Proyecto Final de Microservicios

Sistema de onboarding de empleados construido como un conjunto de microservicios
independientes, cada uno con su propia base de datos, que se comunican de forma
síncrona (HTTP, a través de un API Gateway) y asíncrona (eventos sobre RabbitMQ).


## Índice

- [Servicios del sistema](#servicios-del-sistema)
- [Arquitectura](#arquitectura)
- [Comunicación asíncrona (RabbitMQ)](#comunicación-asíncrona-rabbitmq)
- [API Gateway](#api-gateway)
- [Circuit Breaker de EmpleadoService](#circuit-breaker-de-empleadoservice)
- [Endpoints por servicio](#endpoints-por-servicio)
- [Puesta en marcha](#puesta-en-marcha)
- [Esquemas de base de datos](#esquemas-de-base-de-datos)
- [Pruebas automatizadas](#pruebas-automatizadas)
- [Documentación adicional por servicio](#documentación-adicional-por-servicio)
- [Puntos no verificados / limitaciones conocidas](#puntos-no-verificados--limitaciones-conocidas)

## Servicios del sistema

| Servicio | Carpeta | Tecnología (según código) | Base de datos | Puerto interno |
|---|---|---|---|---|
| `gateway-service` | `gateway-service/` | Java 21, Spring Boot 4.0.8, Spring Cloud Gateway (`spring-cloud.version` 2025.1.3) | — | `8088` (único puerto publicado al host) |
| `empleado-service` | `empleado-service/` | ASP.NET Core (.NET 10) | PostgreSQL 17 (`empleado-db`) | `8080` |
| `departamento-service` | `departamento-service/` | Python 3.11, FastAPI 0.116, SQLAlchemy 2.0 | PostgreSQL 17 (`departamento-db`) | `8081` |
| `notificaciones-service` | `notificaciones-service/` | Node.js ≥20, Express, cliente `pg` sin ORM | PostgreSQL 17 (`notificaciones-db`) | `8082` |
| `perfiles-service` | `perfiles-service/` | Go (`go.mod` declara `go 1.25`), chi router | PostgreSQL 17 (`perfiles-db`) | `8083` |
| `vacaciones-service` | `vacaciones-service/` | Java 21, Spring Boot 4.1.1 | PostgreSQL 17 (`vacaciones-db`) | `8085` |
| `auth-service` | `auth-service/` | Java 21, Spring Boot 4.1.1, Spring Security | PostgreSQL 17 (`auth-db`) | `8089` |
| `message-broker` | imagen `rabbitmq:3-management` | RabbitMQ con plugin de administración | — | `5672` (AMQP) y `15672` (UI), ambos publicados al host |

Todos los contenedores comparten la red `microservicios-network` (definida en
`docker-compose.yml` con `driver: bridge`). Según ese archivo, **el único
servicio de aplicación que publica un puerto al host es `gateway-service`**
(`ports:`); el resto de los microservicios y las bases de datos usan `expose:`,
es decir, solo son alcanzables entre contenedores. `message-broker` es la
excepción: publica sus dos puertos (AMQP y la interfaz de administración) al
host directamente.

## Arquitectura

```text
Cliente HTTP
     |
     | http://localhost:8088
     v
gateway-service (Spring Cloud Gateway)
     |------------|------------|------------|------------|
     v            v            v            v            v
registro-   departamentos-  perfiles-   notificaciones- vacaciones-
service     service         service     service         service
     |            |            |            |               |
     v            v            v            v               v
registro-   departamentos-  perfiles-   notificaciones-  vacaciones-
db          db              db          db               db

empleado-service --- REST síncrono con Circuit Breaker (Polly) ---> departamento-service

empleado-service, perfiles-service, notificaciones-service y vacaciones-service
se conectan además a message-broker (RabbitMQ) para publicar y/o consumir eventos.
```


## Comunicación asíncrona (RabbitMQ)

- El broker es una única instancia de `rabbitmq:3-management` (contenedor
  `rabbitmq-broker`), con un exchange **fanout** llamado `empleados_exchange`
  (declarado como `durable: true` en `empleadoService/infrastructure/messaging/RabbitMqPublisher.cs`).
- `empleado-service` publica en ese exchange los eventos `empleado.creado`,
  `empleado.actualizado` y `empleado.retirado` (ver
  `empleadoService/domain/services/EmpleadoService.cs`).
- `vacaciones-service` publica `vacaciones.programadas`,
  `vacaciones.iniciadas` y `vacaciones.finalizadas` en el mismo exchange después
  del commit. Los eventos incluyen `version: "1.0"` y datos de vacaciones; el
  scheduler y el contrato se documentan en [`vacaciones-service/README.md`](vacaciones-service/README.md).
- `notificaciones-service`, `perfiles-service` y `vacaciones-service` consumen
  ese exchange, cada uno con su propia cola durable y su propia cola de
  mensajes muertos (DLQ):

  | Servicio consumidor | Cola | DLQ | Eventos que procesa |
  |---|---|---|---|
  | `notificaciones-service` | `notificaciones.empleados` | `notificaciones.empleados.dlq` | `empleado.creado`, `empleado.retirado`, `vacaciones.programadas` (otros tipos se reconocen y se descartan sin error) |
  | `perfiles-service` | `perfiles.empleados` | (nombre de cola + `.dlq`) | `empleado.creado`, `empleado.actualizado`, `empleado.retirado` |
  | `vacaciones-service` | `vacaciones.empleados` (por defecto) | `vacaciones.empleados.dlq` | `empleado.creado`, `empleado.actualizado`, `empleado.retirado` (cancela vacaciones `PROGRAMADA` al retirar) |

- Los tres consumidores toleran envelopes en PascalCase o camelCase y usan
  confirmación manual (`ack`/`nack`): un envelope mal formado va directo a la
  DLQ (no tiene sentido reintentarlo); un error transitorio (por ejemplo, la
  base de datos propia caída) se reencola.

## API Gateway

Configuración real en `gateway-service/src/main/resources/application.yml`.

### Tabla de rutas

| Ruta externa | Servicio destino | Variable de entorno que define la URL |
|---|---|---|
| `GET /health` | Resuelto por el propio Gateway (`GatewayController`) | — |
| `/auth/**` | `auth-service` | `AUTH_SERVICE_URL` |
| `/empleados/**` | `empleado-service` | `EMPLEADOS_SERVICE_URL` |
| `/departamentos/**` | `departamento-service` | `DEPARTAMENTO_SERVICE_URL` |
| `/perfiles/**` | `perfiles-service` | `PERFILES_SERVICE_URL` |
| `/notificaciones/**` | `notificaciones-service` | `NOTIFICACIONES_SERVICE_URL` |
| `/vacaciones/**` | `vacaciones-service` | `VACACIONES_SERVICE_URL` |

Cada ruta tiene su propio Circuit Breaker de Resilience4j
(`empleadosServiceCircuitBreaker`, `departamentoServiceCircuitBreaker`, etc.),
todos configurados con la misma plantilla: ventana deslizante de 10 llamadas,
umbral de fallos del 50 % y 10 segundos en estado abierto, con un
`timeLimiter` de 6 segundos por llamada. Si el destino no responde a tiempo o
falla, el Gateway reenvía la petición a `/fallback/{service}`
(`GatewayController.unavailable`), que responde `503 Service Unavailable` con
un cuerpo como este:

```json
{
  "error": "service_unavailable",
  "message": "El microservicio destino no está disponible",
  "service": "departamento-service",
  "path": "/fallback/departamento-service"
}
```

`GET /health` en el Gateway solo confirma que el propio proceso responde
(`{"status": "healthy"}`); no verifica el estado de los servicios destino.

El Gateway valida los Access JWT antes de reenviar rutas protegidas. Comprueba
la firma, expiración, `sub`, rol (`ADMIN` o `USER`) y rechaza los tokens de
recuperación. Las rutas públicas son salud, fallbacks, documentación y
`/auth/login`, `/auth/recover-password` y `/auth/reset-password`. Después de
validar, elimina las cabeceras de identidad recibidas del cliente e inyecta
`X-User-Id` y `X-User-Role` a partir de los claims verificados.

## Circuit Breaker de EmpleadoService

Independientemente del Circuit Breaker del Gateway, `empleado-service`
implementa el suyo propio para su llamada síncrona hacia
`departamento-service`, usando Polly (`DepartamentoCircuitBreaker.cs`,
`DepartamentoClient.cs`).

| Parámetro | Variable de entorno | Rango válido según `DepartamentosResilienceOptions` |
|---|---|---|
| Fallos consecutivos antes de abrir | `CB_FALLOS_CONSECUTIVOS` | 3 a 5 |
| Timeout de cada intento | `CB_TIMEOUT_LLAMADA` | — |
| Duración del estado abierto | `CB_DURACION_CIRCUITO_ABIERTO` | entre 30 y 60 segundos |

El servicio valida estos valores al arrancar (`ValidateOnStart`) y falla si
están fuera de rango. El estado del circuito (`CLOSED`, `OPEN`, `HALF_OPEN`) se
mantiene en un singleton y puede consultarse desde la red interna del
contenedor:

```bash
docker compose exec -T empleado-service curl -s http://localhost:8080/health/circuit-breaker
```

Cuando `departamento-service` no está disponible o el circuito está abierto,
`empleado-service` registra al empleado igualmente con estado
`PENDIENTE_VALIDACION` (fallback orientado a disponibilidad), mientras que un
error propio del dominio (por ejemplo, un `departamentoId` inexistente
respondido con `404`) no activa el fallback y se traduce en `400 Bad Request`.

## Endpoints por servicio

Los siguientes endpoints se verificaron directamente contra el código fuente
de cada servicio (rutas HTTP definidas en su respectivo router/controlador).
Todos son accesibles desde el host únicamente a través del Gateway en
`http://localhost:8088`, salvo indicación contraria.

### `empleado-service` (`/empleados`)

- `POST /empleados` — registra un empleado. Responde `201 Created`
  (`ACTIVO` o `PENDIENTE_VALIDACION` según la disponibilidad de
  `departamento-service`), `400` en errores de validación o departamento
  inexistente, `409` si el email, `numeroEmpleado` o `id` ya existen.
- `GET /empleados/{id}` — consulta un empleado por id. `404` si no existe.
- `PUT /empleados/{id}` — actualiza los datos de un empleado y publica
  `empleado.actualizado`.
- `GET /empleados?estado=RETIRADO&desde=&hasta=` — lista empleados retirados,
  con filtro opcional de fechas. Cualquier otro valor de `estado` (u
  omitirlo) responde `400 Bad Request`, ya que solo se soporta la consulta de
  retirados.
- `DELETE /empleados/{id}` — baja lógica: cambia el estado a `RETIRADO`,
  registra `fechaRetiro`, publica `empleado.retirado` y responde `204`.
- `GET /health` y `GET /health/ready` — liveness y readiness (este último
  valida la conexión a `empleado-db`).
- `GET /health/circuit-breaker` — estado del circuito hacia
  `departamento-service` (ver sección anterior).

### `departamento-service` (`/departamentos`)

- `POST /departamentos` — crea un departamento. `201 Created`.
- `GET /departamentos/{id}` — consulta un departamento por id. `404` si no
  existe.
- `GET /departamentos` — lista todos los departamentos.
- `GET /health` y `GET /health/ready`.

### `perfiles-service` (`/perfiles`)

- `GET /perfiles` — lista los perfiles.
- `GET /perfiles/{empleadoId}` — consulta el perfil de un empleado. `404` si
  no existe.
- `PUT /perfiles/{empleadoId}` — actualiza campos propios del perfil
  (`telefono`, `direccion`, `ciudad`, `biografia`, según su propio README).
- `GET /health/live` y `GET /health/ready`.
- `GET /swagger/*` — documentación Swagger servida por el propio servicio.

### `notificaciones-service` (`/notificaciones`)

- `GET /notificaciones` — historial completo, más reciente primero.
- `GET /notificaciones/{empleadoId}` — historial de un empleado (`[]` si no
  tiene notificaciones; el servicio no valida contra el catálogo de
  empleados).
- `GET /health` y `GET /health/ready`.
- `GET /docs` — documentación Swagger/OpenAPI generada desde el código de las
  rutas.

### `vacaciones-service` (`/vacaciones`)

- `POST /vacaciones` — crea una solicitud de vacaciones (fechas inclusivas,
  sin solapamiento con otro período activo del mismo empleado).
- `GET /vacaciones/{id}` — consulta una solicitud por id.
- `GET /vacaciones?empleadoId=` — lista solicitudes, con filtro opcional por
  empleado.
- `DELETE /vacaciones/{id}` — cancela un período; según su propio README,
  solo aplica a períodos en estado `PROGRAMADA`.
- `GET /health` y `GET /health/ready`.
- `/vacaciones/docs` y `/vacaciones/api-docs` — documentación OpenAPI
  (rutas indicadas en el README propio del servicio).

### `gateway-service`

- `GET /health` — salud del propio Gateway.
- `GET /fallback/{service}` — respuesta de indisponibilidad usada
  internamente por el enrutamiento cuando un Circuit Breaker se abre.

### `auth-service` (`/auth`)

- `POST /auth/login` — autentica credenciales y emite el JWT de acceso.
- `POST /auth/recover-password` — inicia la recuperación de contraseña.
- `POST /auth/reset-password` — consume un token de recuperación.
- `POST /auth/change-password` — cambia la contraseña de una cuenta autenticada.
- `GET /actuator/health` — healthcheck interno usado por Docker Compose.

El servicio no publica su puerto al host; las rutas de negocio se consumen a
través del Gateway. La validación JWT y la autorización RBAC están
centralizadas allí.

### Matriz RBAC del Gateway

| Operación | `USER` | `ADMIN` |
|---|---|---|
| Rutas públicas y `OPTIONS` | Permitido | Permitido |
| `GET`/`HEAD` de empleados, departamentos, perfiles, notificaciones y vacaciones | Permitido | Permitido |
| `POST /auth/change-password` | Permitido | Permitido |
| `PUT /perfiles/{empleadoId}` propio | Permitido | Permitido |
| `PUT /perfiles/{empleadoId}` ajeno | `403 Forbidden` | Permitido |
| Demás operaciones de escritura | `403 Forbidden` | Permitido |

La propiedad del perfil se comprueba comparando `{empleadoId}` con el `sub`
del JWT. Las denegaciones de un usuario autenticado se devuelven como JSON con
estado `403`; un token ausente o inválido conserva la respuesta `401`.

Swagger UI está disponible en `/swagger-ui.html` y la especificación en
`/v3/api-docs`. OpenAPI declara el esquema HTTP `BearerAuth` con formato JWT;
el botón **Authorize** de Swagger UI acepta el access token emitido por
`POST /auth/login` y conserva la autorización mientras se navega por la UI.
La interfaz del Gateway agrega las especificaciones de los servicios en un
selector: Gateway, autenticación, empleados, departamentos, perfiles,
notificaciones y vacaciones. Cada especificación también se puede consultar en
`/openapi/{servicio}` (por ejemplo, `/openapi/vacaciones`). Las rutas OpenAPI se
mantienen públicas para cargar la documentación; las operaciones de negocio
siguen sujetas al JWT y a las reglas RBAC descritas arriba. Para probar
operaciones desde Swagger, primero ejecuta `POST /auth/login`, copia el valor
`token` y pégalo en **Authorize** (Swagger UI añade automáticamente el prefijo
`Bearer`).

Las rutas de especificaciones agregadas son `/v3/api-docs` (Gateway),
`/openapi/auth`, `/openapi/registro`, `/openapi/departamentos`,
`/openapi/perfiles`, `/openapi/notificaciones` y `/openapi/vacaciones`. Algunas
especificaciones también pueden consultarse directamente si el servicio está
accesible: Departamentos en `/docs`, Notificaciones en `/docs`, Perfiles en
`/swagger/index.html` y Vacaciones en `/swagger-ui.html`. En el despliegue
Docker normal, el Gateway es el único puerto de aplicación publicado al host;
por ello se recomienda usar la interfaz agregada.
La especificación de autenticación usa una URL relativa para que Swagger envíe
`/auth/login` al mismo origen del Gateway (por ejemplo, `http://localhost:8088`)
y no intente acceder desde el navegador al hostname interno `auth-service`.

## Puesta en marcha

### Requisitos

- Docker y Docker Compose.
- Un archivo `.env` en la raíz del repositorio con las variables que exige
  `docker-compose.yml` (ver `.env.example` como referencia; todas las
  variables allí marcadas están declaradas como obligatorias mediante la
  sintaxis `${VARIABLE:?...}`, por lo que Compose no arrancará sin ellas).

### Encender el sistema

Ejecutar desde la raíz del repositorio:

```bash
cp .env.example .env   # y ajustar valores si es necesario
docker compose up --build -d
docker compose ps
```

Según las dependencias declaradas en `docker-compose.yml`
(`depends_on` con `condition: service_healthy`), el arranque respeta este
orden: primero las bases de datos y `message-broker`; luego
`departamento-service`; después `empleado-service`, `perfiles-service`,
`notificaciones-service`, `vacaciones-service` y `auth-service` (cada uno depende de su base
de datos y, cuando aplica, de `message-broker`); y por último
`gateway-service`, que depende de que los seis microservicios de aplicación
estén sanos.

Verificación mínima:

```bash
curl -i http://localhost:8088/health
curl -i http://localhost:8088/departamentos
```

### Apagar sin borrar datos

```bash
docker compose down
```

`docker compose down --volumes` elimina los volúmenes (`empleado-data`,
`departamento-data`, `notificaciones-data`, `perfiles-data`,
`vacaciones-data`) y obliga a que las bases de datos vuelvan a inicializarse
desde los scripts en `database/`.

## Esquemas de base de datos

Los scripts iniciales de cada base de datos están en `database/`, organizados
por servicio, y se montan como `/docker-entrypoint-initdb.d` de forma
`read-only` en el contenedor de PostgreSQL correspondiente:

```text
database/
  empleado/001-schema.sql
  departamentos/001-schema.sql
  notificaciones/001-schema.sql
  perfiles/001-schema.sql
  vacaciones/001-schema.sql
```

## Pruebas automatizadas

Cada servicio mantiene su propia suite de pruebas, en su propia carpeta.
Para este cambio se ejecutó la suite de `vacaciones-service` (17 pruebas
correctas); las demás filas indican cómo correr sus suites:

| Servicio | Cómo correr las pruebas | Archivos de prueba encontrados |
|---|---|---|
| `empleado-service` | `cd empleado-service && dotnet test EmpleadoService.sln` | `EmpleadosEndpointsTests.cs`, `EmpleadoServiceTests.cs`, `EmpleadoRepositoryTests.cs`, `DepartamentosResilienceOptionsTests.cs`, `DepartamentoClientTests.cs` |
| `departamento-service` | `cd departamento-service && pip install -r requirements-dev.txt && pytest -q` | `test_departamentos.py`, `test_resiliencia.py` |
| `notificaciones-service` | `cd notificaciones-service && npm test` (Jest + Supertest) | `eventoParser.test.js`, `notificacionService.test.js`, `api.test.js` |
| `perfiles-service` | `cd perfiles-service && go test ./...` | `internal/api/router_test.go`, `internal/messaging/consumer_test.go` |
| `vacaciones-service` | `mvn -f vacaciones-service/pom.xml test` | Validación de fechas, scheduler, transiciones tras retiro y payload RabbitMQ |
| `auth-service` | `cd auth-service && ./mvnw test` | `AuthServiceApplicationTests.java`, `AccountLifecycleServiceTest.java`, `JsonConverterAndConsumerTest.java` |
| `gateway-service` | `cd gateway-service && mvn test` | Pruebas RBAC, autenticación JWT y autorización de propiedad de perfil |

## Documentación adicional por servicio

Varios servicios incluyen su propio `README.md` con más detalle del que cabe
aquí (variables de entorno completas, estructura interna, justificación de
decisiones técnicas):

- [`empleado-service/README.md`](empleado-service/README.md)
- [`perfiles-service/README.md`](perfiles-service/README.md)
- [`notificaciones-service/README.md`](notificaciones-service/README.md) (incluye además `DOC/Notificaciones-Diseno.md`)
- [`vacaciones-service/README.md`](vacaciones-service/README.md)
- [`docs/evidencias/reto5/README.md`](docs/evidencias/reto5/README.md) y su [colección Postman](docs/evidencias/reto5/reto5-integracion.postman_collection.json)
- [Flujo de empleado con autenticación JWT desde PowerShell](docs/evidencias/reto5/pruebas-flujo-auth-powershell.md)

`departamento-service` y `gateway-service` no tienen un `README.md` propio
en el repositorio.

## Puntos no verificados / limitaciones conocidas

- `VacationScheduler` asume una sola instancia; N réplicas pueden publicar N
  veces. El Reto 31 incorporará coordinación distribuida con ShedLock.
