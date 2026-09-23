# notificaciones-service

Reto 4 — **Integrante 2**. Consume eventos publicados por `empleados-service`
(`empleado.creado`, `vacaciones.programadas`) desde RabbitMQ, simula el envío de una
notificación (log) y guarda un historial consultable, con deduplicación por id de evento.

No modifica código de ningún otro servicio del repositorio: en `docker-compose.yml` se
agregaron sus propios dos bloques (`notificaciones-db` y `notificaciones-service`), el
volumen `notificaciones-data`, y cuatro variables de entorno a `registro-service`
(`RabbitMQ__Host` y credenciales) que le faltaban para poder conectarse al broker
dentro de Docker Compose — sin esa configuración, `empleado.creado` nunca salía de su
contenedor y este servicio no recibía nada. Ese cambio se coordinó primero con el
responsable de `registro-service` (Integrante 1). No se tocó `gateway-service` ni
`message-broker`. El detalle está en la sección 7 de
[`DOC/Notificaciones-Diseno.md`](DOC/Notificaciones-Diseno.md).

## Stack

Node.js 20 + Express, `pg` (PostgreSQL sin ORM) y `amqplib` (cliente de RabbitMQ).
Elegido porque el equipo ya cubre .NET (RegistroService), Python (DepartamentosService)
y Java (Gateway); no había un patrón de repositorio único que seguir para un nuevo
microservicio.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/notificaciones` | Historial completo, más reciente primero. |
| `GET` | `/notificaciones/{empleadoId}` | Historial de un empleado. `[]` si no tiene. |
| `GET` | `/health` | Liveness: el proceso está vivo. |
| `GET` | `/health/ready` | Readiness: además, la base de datos responde. |
| `GET` | `/docs` | Swagger UI (OpenAPI generado desde el código de las rutas). |

`notificaciones-service` publica su puerto directamente al host (`8082` por defecto,
`NOTIFICACIONES_SERVICE_PORT` en `.env`): a diferencia de `registro-service` y
`departamentos-service`, no pasa por el Gateway. Integrarlo al Gateway no forma parte
de lo pedido para este servicio (ver `DOC/Notificaciones-Diseno.md`).

## Cómo correrlo

### Con Docker Compose (recomendado; junto al resto del sistema)

Desde la raíz del repositorio:

```bash
docker compose up --build -d
docker compose ps          # notificaciones-service y notificaciones-db en "healthy"
curl http://localhost:8082/notificaciones
```

### Local, sin Docker (para desarrollar)

Requiere Node 20+, un Postgres y un RabbitMQ accesibles (por ejemplo, levantando solo
esas dos piezas con Compose: `docker compose up -d notificaciones-db message-broker`,
que sí publican sus puertos al host).

```bash
cd notificaciones-service
npm install
DB_HOST=localhost DB_NAME=notificaciones_db DB_USER=notificaciones_user \
DB_PASSWORD=notificaciones_password RABBITMQ_HOST=localhost \
npm run dev
```

Todas las variables de entorno (con sus valores por defecto) están documentadas en
[`src/config/env.js`](src/config/env.js).

## Pruebas

```bash
cd notificaciones-service
npm test
```

22 pruebas con Jest y Supertest, todas sobre lógica y HTTP en memoria (sin Postgres ni
RabbitMQ reales, así que corren igual de rápido en cualquier máquina):

- `test/eventoParser.test.js`: extracción de campos del *envelope* (tolerante a
  PascalCase/camelCase) y construcción del texto de la notificación.
- `test/notificacionService.test.js`: deduplicación por `eventoId`, filtrado de tipos de
  evento no soportados, y separación del historial por empleado — con un repositorio
  falso en memoria en vez de Postgres.
- `test/api.test.js`: los endpoints HTTP con un `notificacionService` falso, vía
  Supertest.

## Deduplicación

`evento_id` (el campo `Id` del *envelope*, no el id del empleado) tiene una restricción
`UNIQUE` en la base de datos. Guardar una notificación es un
`INSERT ... ON CONFLICT (evento_id) DO NOTHING`: si RabbitMQ reentrega el mismo mensaje
(entrega *at-least-once*, nunca *exactly-once*), la segunda vez no crea una fila
duplicada. Ver la sección "Deduplicación" de `DOC/Notificaciones-Diseno.md` para el
razonamiento completo.

## Estructura

```
notificaciones-service/
  src/
    config/env.js              Variables de entorno y sus valores por defecto
    db/pool.js                 Pool de conexiones a Postgres
    domain/
      eventoParser.js          Traduce el envelope crudo a los datos que necesita el servicio
      notificacionRepository.js Acceso a datos (INSERT con dedup, listados)
      notificacionService.js   Caso de uso: procesar evento, listar historial
    messaging/rabbitConsumer.js Conexión a RabbitMQ, cola propia, reintentos, DLQ
    api/
      notificacionesRouter.js  GET /notificaciones, GET /notificaciones/{empleadoId}
      healthRouter.js          GET /health, GET /health/ready
    docs/swagger.js            Documento OpenAPI generado desde los comentarios de las rutas
    app.js                     Construye la app de Express (sin escuchar ni conectar RabbitMQ)
    server.js                  Punto de entrada: arranca la app y el consumidor
  test/                        Pruebas con Jest + Supertest
  init.sql                     Esquema de la tabla notificaciones
  Dockerfile
  DOC/Notificaciones-Diseno.md Qué se hizo, en qué archivos y por qué
```
