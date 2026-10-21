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

## Pruebas Automatizadas y Reportes (Reto 6)

### Pirámide de pruebas

| Nivel | Qué valida | Dependencias | Dónde |
|---|---|---|---|
| Unitarias (base) | Reglas de negocio de una sola clase, con test doubles (mocks/fakes) | Ninguna: no necesitan Docker ni BD | `vacaciones-service` (JUnit 5 + Mockito), `empleado-service` (xUnit), `auth-service`, `gateway-service`, `departamento-service` (pytest), `notificaciones-service` (Jest), `perfiles-service` (go test) |
| Integración (medio) | Consultas SQL y restricciones contra una BD real | Docker (Testcontainers, `postgres:17-alpine`) | `vacaciones-service`: `VacationRepositoryIntegrationTest` |
| E2E / BDD (cima) | Flujos de negocio completos a través del gateway, RabbitMQ y las BD reales | Stack completo de `docker compose` | `e2e-tests/` |

**BDD** (Behavior-Driven Development): los escenarios se escriben en Gherkin en español (`Dado / Cuando / Entonces`),
así negocio y QA leen el mismo documento ejecutable. Se usa **Cucumber-JVM** con Rest Assured. Los resultados asincrónicos
(RabbitMQ) se verifican con *polling* con reintentos (`support/Polling.java`: 15 intentos, cada 2 s, tope de 90 s), sin pausas fijas.

### Reportes por suite

Cada suite se ejecuta con un único comando y falla con código de salida distinto de 0 si alguna prueba falla.

| Suite | Comando (desde la raíz del repo) | Resultados de pruebas | Cobertura |
|---|---|---|---|
| `vacaciones-service` (unit + integración) | `mvn -f vacaciones-service/pom.xml verify` | `vacaciones-service/target/surefire-reports/*.xml` (JUnit XML) | `vacaciones-service/target/site/jacoco/index.html` y `jacoco.xml` (mínimo 70 % de líneas; actual 87,8 %) |
| `empleado-service` | `dotnet test empleado-service/empleadoService.Tests/EmpleadoService.Tests.csproj --collect:"XPlat Code Coverage" --logger trx --settings empleado-service/empleadoService.Tests/EmpleadoService.Tests.runsettings` | `empleado-service/empleadoService.Tests/TestResults/*.trx` | `empleado-service/empleadoService.Tests/TestResults/<GUID>/coverage.cobertura.xml` |
| `auth-service` | `cd auth-service && ./mvnw clean verify` | `auth-service/target/surefire-reports/*.xml` | `auth-service/target/site/jacoco/index.html` y `jacoco.xml` (71,0 % de líneas; solo reporte, sin umbral) |
| **`e2e-tests` (BDD)** | `cd e2e-tests && mvn clean test` | `e2e-tests/reports/junit.xml` (JUnit XML)<br>`e2e-tests/reports/cucumber.json` (Cucumber JSON) | `e2e-tests/reports/cucumber.html` (informe HTML navegable) |

Los reportes BDD se generan en `e2e-tests/reports/` en cada ejecución (carpeta ignorada por git, no se versiona); Jenkins (Reto 7) los consume desde esa ruta.

### Ejecución de las pruebas BDD (paso a paso)

**Prerrequisitos:** Docker Desktop encendido, JDK 21, Maven 3.9+ y el archivo `.env` de la raíz creado a partir de `.env.example`.
Para el ciclo de vacaciones la suite usa los endpoints manuales del scheduler, así que `.env` debe tener `VACACIONES_MANUAL_ENABLED=true`
(el valor por defecto es `false`).

1. Levantar el sistema desde la raíz: `docker compose up -d --build` y esperar a que los contenedores estén `healthy` (`docker compose ps`).
2. Exportar las variables de la suite (ver `e2e-tests/.env.example`): como mínimo `E2E_ADMIN_EMAIL`, `E2E_ADMIN_PASSWORD` (los `ADMIN_*` del `.env`)
   y `E2E_JWT_SECRET` (el `JWT_SECRET_KEY` del `.env`). Las credenciales USER son opcionales: si no se definen, la suite crea una cuenta USER efímera.
3. Ejecutar: `cd e2e-tests && mvn clean test`. Solo el smoke: `mvn test -Dcucumber.filter.tags="@smoke"`.
4. Abrir `e2e-tests/reports/cucumber.html` en el navegador.

**Escenarios implementados (19):**
* **Smoke (`smoke.feature`):** `/health` del gateway y OpenAPI público.
* **Seguridad (`seguridad.feature`):** token ausente y malformado (401), USER solo lectura (403 al escribir) y ADMIN con acceso completo.
* **Onboarding (`onboarding.feature`):** registro, creación asincrónica de la cuenta, notificación de bienvenida, establecimiento de contraseña con login, y errores por departamento inexistente o campos faltantes.
* **Vacaciones (`vacaciones.feature`):** programación exitosa, rechazo por fechas incoherentes, rechazo por solapamiento y ciclo completo de suspensión y reactivación de la cuenta (inicio y fin forzados con los endpoints manuales).
* **Offboarding (`offboarding.feature`):** retiro del empleado, login bloqueado, recuperación de contraseña fallida y consulta de auditoría (`GET /empleados?estado=RETIRADO`).

# Reto 7: Integración Continua con Jenkins y SonarQube

Esta sección documenta detalladamente la arquitectura, configuración, ejecución y sustentación del **Reto 7: Integración Continua (CI/CD)** para el ecosistema distribuido de microservicios.

---

## 1. Accesos Rápidos y Credenciales del Entorno CI/CD

| Servicio | URL Local | Credenciales por Defecto | Propósito |
|---|---|---|---|
| **Jenkins** | [http://localhost:9090](http://localhost:9090) | Usuario: `admin`<br>Clave: `ContraseniaJenkins` | Servidor de automatización y orquestación de pipelines CI/CD |
| **SonarQube** | [http://localhost:9000](http://localhost:9000) | Usuario: `admin`<br>Clave: `admin123` | Plataforma de inspección continua y Quality Gate |
| **API Gateway** | [http://localhost:8088](http://localhost:8088) | N/A (Admin: `admin@empresa.com` / `admin123`) | Punto de entrada único para pruebas E2E |
| **RabbitMQ UI** | [http://localhost:15672](http://localhost:15672) | Usuario: `admin`<br>Clave: `cambiar_esta_contrasenia` | Broker de mensajería asíncrona |

---

## 2. Cumplimiento de Especificaciones (Punto por Punto)

### Punto 1: Infraestructura Jenkins como Código (JCasC) y CLI
* **Servicio en Docker Compose:** El servicio `jenkins` está integrado en el `docker-compose.yml` raíz, exponiendo el puerto web `9090:8080` y el puerto de agentes `50000:50000`.
* **Construcción con Plugins Preinstalados:** Se utiliza un [`jenkins/Dockerfile`](file:///c:/ING%20SISTEMAS/novenoSemestre/microservicios/reto01C/proyecto-final-microservicios/jenkins/Dockerfile) basado en `jenkins/jenkins:lts-jdk21` que instala automáticamente la CLI oficial de Docker (`docker.io`), Docker Compose v2 (`docker-compose-v2`), y la lista requerida de plugins:
  * `docker-workflow`, `workflow-aggregator`, `pipeline-stage-view`, `git`
  * `configuration-as-code` (JCasC), `job-dsl`
  * `plain-credentials`, `sonar` (SonarQube Scanner)
  * `junit`, `coverage` (soporte JaCoCo y Cobertura), `cucumber-reports`, `htmlpublisher`, `mstest`
* **Acceso al Docker Socket:** Monta `/var/run/docker.sock` del host y gestiona permisos con `group_add: ["${DOCKER_GID:-0}"]`, permitiendo a Jenkins orquestar contenedores hermanos mediante Docker-out-of-Docker (DooD).
* **Configuración como Código (JCasC):** Mediante [`jenkins/casc.yaml`](file:///c:/ING%20SISTEMAS/novenoSemestre/microservicios/reto01C/proyecto-final-microservicios/jenkins/casc.yaml), Jenkins arranca 100% configurado sin asistentes manuales:
  * Usuario administrador y control de acceso basado en roles/matriz.
  * Credenciales secretas registradas automáticamente (como `sonarqube-token`).
  * Conexión con el servidor SonarQube (`http://sonarqube:9000`).
  * Creación automática de los jobs multietapa (`auth-service` y `empleado-service`) mediante Job DSL apuntando al repositorio de Git.
* **Jenkins CLI:** Disponible mediante el cliente `jenkins-cli.jar` incluido en la raíz. Permite consultar el estado de los jobs y disparar builds desde consola:
  ```powershell
  java -jar jenkins-cli.jar -s http://localhost:9090/ -auth admin:ContraseniaJenkins list-jobs
  java -jar jenkins-cli.jar -s http://localhost:9090/ -auth admin:ContraseniaJenkins build auth-service -s -v
  ```

---

### Punto 2: Pipeline CI Declarativo para Microservicio Principal (`auth-service`)
* **Ubicación:** [`auth-service/Jenkinsfile`](file:///c:/ING%20SISTEMAS/novenoSemestre/microservicios/reto01C/proyecto-final-microservicios/auth-service/Jenkinsfile) implementado con sintaxis declarativa (`pipeline { ... }`).
* **Aislamiento en Contenedores Efímeros:** Usa agentes Docker a nivel de etapa (`agent { docker { image 'maven:3.9-eclipse-temurin-21' ... } }`) evitando instalar Java o Maven en el servidor Jenkins.
* **Caché Persistente:** Se monta el volumen `maven_repo_cache` en `/tmp` con `-Dmaven.repo.local=/tmp/.m2/repository`, acelerando los builds consecutivos.
* **Etapa Build:** Ejecuta compilación limpia (`mvn clean compile -DskipTests`).
* **Etapa Test y Cobertura:** 
  * Ejecuta la suite de pruebas unitarias con Surefire y genera el reporte de cobertura JaCoCo (`jacoco.xml`).
  * Configura `-Dmaven.test.failure.ignore=true` para garantizar que Surefire termine y el paso `post` publique siempre los reportes de prueba.
* **Publicación de Resultados:**
  * **JUnit XML:** Publicado con la instrucción `junit testResults: 'auth-service/target/surefire-reports/*.xml'`.
  * **Cobertura JaCoCo:** Publicada con el plugin `recordCoverage` leyendo `auth-service/target/site/jacoco/jacoco.xml`.
* **Manejo de Estados:**
  * Si hay error de compilación o sintaxis: el pipeline finaliza en **FAILURE** (rojo).
  * Si compila pero fallan pruebas unitarias: el paso `junit` marca el build como **UNSTABLE** (amarillo), impidiendo el paso a etapas posteriores como empaquetado o despliegue.

---

### Punto 3: Inspección Continua con SonarQube y Quality Gate Estricto
* **Infraestructura SonarQube:** Desplegado mediante docker-compose.yml con dos contenedores:
  * sonar-db: PostgreSQL 16 exclusivo para SonarQube con volumen persistente sonar_db_data.
  * sonarqube: SonarQube LTS Community Edition (v9.9.8) en el puerto 9000:9000.
* **Red y Conectividad:** Ambos servicios conviven en la red interna microservicios-network. Los agentes Docker de Jenkins se comunican directamente mediante http://sonarqube:9000.
* **Cómo se configuró el Quality Gate (QualityGate70):**
  1. **Creación del Gate:** Se creó el perfil de calidad QualityGate70 mediante la API REST de SonarQube (POST /api/qualitygates/create?name=QualityGate70).
  2. **Condición de Cobertura Estricta:** Se añadió la regla obligatoria donde la cobertura general menor al 70 % genera fallo inmediato (POST /api/qualitygates/create_condition?gateId=...&metric=coverage&error=70&op=LT).
  3. **Quality Gate por Defecto:** Se estableció QualityGate70 como el Quality Gate predeterminado global (POST /api/qualitygates/set_as_default), asegurando que tanto uth-service como empleado-service sean evaluados con la misma exigencia sin configuración manual por proyecto.
* **Webhook Bidireccional SonarQube ➔ Jenkins:**
  * En SonarQube se encuentra registrado el webhook con URL http://jenkins:8080/sonarqube-webhook/.
  * Cuando el Compute Engine (CE) de SonarQube termina de calcular las métricas, notifica asíncronamente a Jenkins con el veredicto (PASSED o FAILED).
* **Etapas en el Pipeline:**
  * stage('SonarQube Analysis'): Envuelto en withSonarQubeEnv('sonarqube'), inyecta las variables SONAR_HOST_URL y SONAR_AUTH_TOKEN. Ejecuta el escáner correspondiente e importa el reporte de cobertura (JaCoCo en Java, OpenCover en .NET).
  * stage('Quality Gate'): Invoca 	imeout(time: 5, unit: 'MINUTES') { waitForQualityGate abortPipeline: true }. Jenkins escucha el webhook; si el Quality Gate no pasa la cobertura (≥ 70 %) o encuentra vulnerabilidades críticas, aborta el pipeline de inmediato marcando **FAILURE**.

---

### Punto 4: Empaquetado Docker, Pruebas E2E y Segundo Pipeline Multilenguaje

#### A. Empaquetado (`stage('Package')`)
* Construye la imagen Docker del microservicio asignándole dos etiquetas: la versión del build (`auth-service:${BUILD_NUMBER}`) y la etiqueta de última versión (`auth-service:latest`).

#### B. Pruebas E2E Multicontenedor (`stage('E2E Tests')`)
* **Aislamiento de Infraestructura:** Define la variable `APP_SERVICES` con los 14 servicios y bases de datos de negocio (`gateway-service`, `empleado-service`, `departamento-service`, `perfiles-service`, `notificaciones-service`, `vacaciones-service`, `auth-service`, `message-broker`, etc.), protegiendo los contenedores de CI (`jenkins`, `sonarqube`, `sonar-db`) de ser detenidos.
* **Despliegue DooD:** Prepara el archivo `.env` y levanta la aplicación con `docker compose up -d --build $APP_SERVICES`.
* **Bases de Datos Autocontenidas:** Cada servicio Postgres (`vacaciones-db`, `departamento-db`, etc.) construye su imagen con Dockerfile propio que incluye su script de inicialización (`001-schema.sql`), garantizando total compatibilidad en entornos Docker-out-of-Docker sin depender de rutas del host.
* **Verificación de Salud:** Realiza un bucle de sondeo sobre `docker compose ps` verificando que todos los microservicios alcancen el estado `healthy`.
* **Ejecución BDD Cucumber:** Se ejecuta la suite de pruebas completa en `e2e-tests` contra `http://gateway-service:8088`.
* **Publicación de Reportes E2E:**
  * **JUnit XML:** Resultados máquina registrados con `junit testResults: 'e2e-tests/reports/junit.xml'`.
  * **Reporte BDD Interactivo:** Publicado con el plugin `cucumber-reports` a partir de `**/cucumber.json`.
  * **Reporte HTML Navegable:** Publicado con el plugin `htmlpublisher` bajo el enlace lateral **"Reporte BDD"** (`cucumber.html`).
  * **Diagnóstico de Contenedores:** Exporta y archiva los logs completos de todos los microservicios (`docker compose logs > docker-logs.txt`) mediante `archiveArtifacts`.
* **Limpieza Garantizada:** En el bloque `post { always { ... } }`, apaga los contenedores y elimina los volúmenes de datos (`microservicios_*`) para asegurar idempotencia total.

#### C. Segundo Pipeline Multilenguaje (empleado-service en C# / .NET 10)
Ambos pipelines (uth-service y empleado-service) cuentan con **las mismas 7 etapas exactas**, incluyendo el análisis estricto de SonarQube y el Quality Gate:

1. Checkout
2. Build
3. Test (con reporte de pruebas y cobertura)
4. SonarQube Analysis
5. Quality Gate
6. Package
7. E2E Tests

##### ¿Por qué se creó la imagen dotnet-sonar:10.0 (jenkins/Dockerfile.dotnet-sonar)?
Para que el equipo y el profesor comprendan fácilmente el motivo técnico:
1. **El escáner de .NET requiere Java:** dotnet-sonarscanner se conecta a MSBuild y corre analizadores de Roslyn en C#, pero el proceso que consolida el análisis, indexa los archivos fuente y sube los reportes al servidor SonarQube es un binario Java (CLI). La imagen oficial de Microsoft (mcr.microsoft.com/dotnet/sdk:10.0) **no incluye Java**, lo que causaba un fallo inmediato al intentar escanear.
2. **Compatibilidad con SonarQube LTS (v9.9.8):** Las versiones recientes del escáner (dotnet-sonarscanner v12+) rompieron compatibilidad con SonarQube 9.x/10.x LTS requiriendo SonarQube 2025+. Por ello, se especificó la versión 9.2.1, 100 % compatible con la versión comunitaria LTS del proyecto.
3. **Instalación y permisos en agentes efímeros de Jenkins:** Los agentes de Jenkins se ejecutan con el usuario 1000:1000 (no-root). Al instalar la herramienta con --tool-path /usr/local/bin y permisos 777, cualquier usuario dentro del contenedor puede invocar dotnet-sonarscanner sin conflictos de permisos.
4. **Solución limpia e inmutable:** En lugar de instalar paquetes manualmente en el contenedor de Jenkins o en la máquina host, se empaquetó jenkins/Dockerfile.dotnet-sonar que combina .NET 10 SDK + OpenJDK 17 JRE Headless + dotnet-sonarscanner 9.2.1.

* **Compilación:** dotnet build EmpleadoService.sln -c Release.
* **Pruebas y Cobertura:** Ejecuta dotnet test generando resultados en formato MSTest (	est-results.trx) y cobertura dual con Cobertura y OpenCover (coverage.cobertura.xml y coverage.opencover.xml).
* **Análisis SonarQube y Quality Gate:** SonarQube consume coverage.opencover.xml excluyendo las clases autogeneradas de migraciones (**/migrations/**), alcanzando un **74.6 % de cobertura real** y aprobando el Quality Gate (≥ 70 %).
* **Publicación de Reportes:**
  * Plugin mstest procesando **/test-results.trx.
  * Plugin 
ecordCoverage leyendo **/coverage.cobertura.xml.
* **Empaquetado y E2E:** Construye empleado-service: y ejecuta la suite BDD Cucumber completa contra el stack de microservicios.

---

## 3. Tabla de Enrutamiento de Reportes (¿Dónde ver cada métrica?)

Para sustentar ante el profesor o jurado, consulta la siguiente tabla de navegación en la interfaz de Jenkins y SonarQube:

| Métrica / Reporte | Herramienta | Ruta de Navegación Exacta en la UI | Qué muestra |
|---|---|---|---|
| **Resultados de Pruebas Unitarias** | Jenkins | `Job (auth-service o empleado-service)` $\to$ `Build #X` $\to$ **Test Result** | Total de pruebas pasadas/fallidas, tiempo de ejecución y detalle de aserciones. |
| **Cobertura de Código JaCoCo / Cobertura** | Jenkins | `Job` $\to$ `Build #X` $\to$ **Coverage** o **Coverage Report** | Gráfico de porcentaje de cobertura por líneas, ramas, clases y métodos. |
| **Calidad de Código y Quality Gate** | SonarQube | [http://localhost:9000](http://localhost:9000) $\to$ Proyecto `auth-service` | Semáforo verde **Passed**, cobertura ($\ge 70\%$), 0 bugs, 0 vulnerabilidades, code smells y duplicación. |
| **Webhook de SonarQube** | SonarQube | [http://localhost:9000](http://localhost:9000) $\to$ `Administration` $\to$ `Configuration` $\to$ `Webhooks` | Historial de entregas exitosas (código HTTP 200) hacia Jenkins. |
| **Reporte Gráfico BDD (Cucumber)** | Jenkins | `Job` $\to$ `Build #X` $\to$ **Cucumber Reports** o menú lateral **Reporte BDD** | Gráficas circulares con los 19 escenarios BDD, pasos aprobados y tiempos de respuesta. |
| **Logs de Diagnóstico de Microservicios** | Jenkins | `Job` $\to$ `Build #X` $\to$ **Artifacts** $\to$ `docker-logs.txt` | Registros completos de los 14 contenedores Docker durante la prueba E2E. |

---

## 4. Guía de Sustentación y Demostración de Fallos (Punto 5)

### A. Demostración de Idempotencia y Reproducibilidad (3 Ejecuciones Consecutivas)
Para demostrar que el pipeline es completamente estable, aislado y no sufre de efectos secundarios por ejecuciones previas:
1. En Jenkins, ingresar a `auth-service` y hacer clic en **"Construir ahora"** (**Build Now**) 3 veces consecutivas (o mediante CLI):
   ```powershell
   java -jar jenkins-cli.jar -s http://localhost:9090/ -auth admin:ContraseniaJenkins build auth-service -s -v
   ```
2. **Resultado esperado:** Los tres builds finalizan en color **Verde (SUCCESS)** sin intervención manual. La limpieza pre y post asegura que ni los volúmenes de base de datos ni los contenedores interfieran entre sí.

---

### B. Simulación de los 4 Tipos de Fallo Requeridos

El profesor puede solicitar evidenciar cómo reacciona Jenkins ante distintos escenarios de error. A continuación se detallan los pasos para simular y explicar cada uno:

#### Caso 1: Fallo en Prueba Unitaria
* **Cómo simularlo:** Modificar [`auth-service/src/test/java/co/edu/uniquindio/auth_service/service/AuthServiceImplTest.java`](file:///c:/ING%20SISTEMAS/novenoSemestre/microservicios/reto01C/proyecto-final-microservicios/auth-service/src/test/java/co/edu/uniquindio/auth_service/service/AuthServiceImplTest.java) alterando una aserción (ej. cambiar un `assertTrue(...)` por `assertFalse(true)`).
* **Resultado en Jenkins:** 
  * La etapa `Test` se ejecuta completamente.
  * El estado del build pasa a **UNSTABLE** (amarillo).
  * Al ingresar a **Test Result**, se señala con precisión quirúrgica el método de prueba que falló y la traza de la aserción.
  * Las etapas posteriores (`Package`, `E2E Tests`) no se ejecutan indebidamente.

#### Caso 2: Incumplimiento del Quality Gate (Cobertura $< 70\%$)
* **Cómo simularlo:** En SonarQube ([http://localhost:9000](http://localhost:9000)), ir a `Quality Gates` $\to$ `QualityGate70`, editar temporalmente la condición de cobertura subiéndola al $95\%$, y ejecutar el build de `auth-service`.
* **Resultado en Jenkins:**
  * Compilación y pruebas unitarias pasan en verde.
  * SonarQube analiza el código y calcula la cobertura real (aprox. 85%).
  * La etapa `Quality Gate` espera la notificación del Webhook y detecta el incumplimiento ($85\% < 95\%$).
  * `waitForQualityGate` aborta el pipeline marcando **FAILURE** (rojo) en la etapa `Quality Gate`.
* *(Restauración: Volver a dejar el umbral en 70% en SonarQube).*

#### Caso 3: Error de Sintaxis en `Dockerfile`
* **Cómo simularlo:** En [`auth-service/Dockerfile`](file:///c:/ING%20SISTEMAS/novenoSemestre/microservicios/reto01C/proyecto-final-microservicios/auth-service/Dockerfile), agregar al final una instrucción con sintaxis inválida (ejemplo: `COMANDO_INVALIDO test`).
* **Resultado en Jenkins:**
  * Las etapas `Build`, `Test`, `SonarQube` y `Quality Gate` completan con éxito.
  * La etapa `Package` intenta ejecutar `docker build` y falla de inmediato.
  * El pipeline se detiene en **FAILURE** (rojo) exactamente en la etapa `Package`.
  * La etapa `E2E Tests` no se ejecuta, impidiendo desplegar una imagen rota.

#### Caso 4: Fallo en Escenario BDD (Pruebas E2E)
* **Cómo simularlo:** En [`e2e-tests/src/test/resources/features/smoke.feature`](file:///c:/ING%20SISTEMAS/novenoSemestre/microservicios/reto01C/proyecto-final-microservicios/e2e-tests/src/test/resources/features/smoke.feature), modificar el código de respuesta esperado de `/health` de `200` a `500`.
* **Resultado en Jenkins:**
  * Todo el ecosistema de 14 contenedores se despliega y alcanza estado `healthy`.
  * La suite de Cucumber corre y detecta que el escenario falló.
  * El paso `cucumber-reports` cambia el estado del build a **UNSTABLE** (amarillo).
  * En el menú **Reporte BDD** / **Cucumber Reports**, el escenario aparece claramente resaltado en rojo con la diferencia entre el valor esperado (`500`) y el obtenido (`200`).
  * En `Post Actions`, se archiva `docker-logs.txt` para análisis forense y todos los contenedores se apagan limpiamente.
