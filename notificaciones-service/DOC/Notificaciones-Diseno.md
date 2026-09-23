# notificaciones-service — Qué se hizo, en qué archivos y por qué

Reto 4, responsabilidad de **Integrante 2**. Este documento sigue el mismo formato que
[`Reto3-CircuitBreaker.md`](../../Reto-1/RegistroService/RegistroService/DOC/Reto3-CircuitBreaker.md):
qué se creó, dónde, y el razonamiento detrás de cada decisión.

## 1. Objetivo y alcance

| Requisito | Resultado |
|-----------|-----------|
| Servicio y base de datos propios | `notificaciones-service` (Node/Express) + `notificaciones-db` (Postgres), ambos nuevos en `docker-compose.yml` |
| Consumir `empleado.creado` y `vacaciones.programadas` | Consumidor de RabbitMQ ligado al exchange fanout `empleados_exchange` |
| Generar logs simulando notificaciones | `console.log` por cada notificación procesada (ver `notificacionService.js`) |
| Guardar historial | Tabla `notificaciones`, con el *payload* crudo del evento en una columna `JSONB` |
| `GET /notificaciones` y `GET /notificaciones/{empleadoId}` | Implementados |
| Deduplicación por id del evento | `UNIQUE (evento_id)` + `ON CONFLICT DO NOTHING` |
| Dockerfile + Swagger/OpenAPI | Implementados |

Explícitamente **fuera** de este alcance (responsabilidad de otros integrantes, según
la asignación de tareas del Reto 4): `perfiles-service` (Integrante 3, consume
`empleado.actualizado`/`empleado.retirado`) y `vacaciones-service` (Integrante 4, que
además publica `vacaciones.programadas` e integra `/vacaciones` al Gateway). No se creó
ni se tocó código de ninguno de los dos.

## 2. Por qué Node.js/Express

El repositorio ya usa tres stacks distintos para sus tres servicios (.NET para
RegistroService, Python/FastAPI para DepartamentosService, Java/Spring para el
Gateway), así que no existía un patrón único de "el próximo microservicio se hace
así". Se decidió por Node/Express como una cuarta opción liviana, sin dependencias del
resto del proyecto (ni siquiera comparte librerías con `empleados-service`, que
también usa RabbitMQ pero desde .NET).

## 3. Arquitectura interna

```
RabbitMQ (empleados_exchange, fanout)
        │
        ▼
messaging/rabbitConsumer.js  ── parsea JSON, filtra tipos soportados
        │
        ▼
domain/eventoParser.js       ── valida el envelope, arma el texto de la notificación
        │
        ▼
domain/notificacionService.js ── decide si es nueva o duplicada, hace el log
        │
        ▼
domain/notificacionRepository.js ── INSERT ... ON CONFLICT DO NOTHING
        │
        ▼
Postgres (tabla notificaciones)
        ▲
        │
api/notificacionesRouter.js  ── GET /notificaciones, GET /notificaciones/{empleadoId}
```

La capa de dominio (`eventoParser`, `notificacionService`) no importa `pg` ni
`amqplib` directamente: recibe el repositorio y los datos ya parseados por parámetro.
Es lo que permite probar la deduplicación y la construcción de mensajes con un
repositorio falso en memoria (`test/notificacionService.test.js`), sin levantar
Postgres ni RabbitMQ para correr `npm test`.

## 4. Archivos creados

### Servicio

| Archivo | Qué contiene |
|---|---|
| `src/config/env.js` | Toda la configuración (puerto, credenciales de BD y RabbitMQ, nombre del exchange/cola, tipos de evento soportados), con valores por defecto para desarrollo local. |
| `src/db/pool.js` | Pool de conexiones de `pg`, compartido por las rutas HTTP y el consumidor. |
| `src/domain/eventoParser.js` | Traduce el *envelope* JSON crudo a `{eventoId, tipoEvento, empleadoId, data}` y construye el texto de la notificación simulada. Ver §5 sobre el formato del *envelope*. |
| `src/domain/notificacionRepository.js` | Acceso a datos: `guardarSiEsNueva`, `listarTodas`, `listarPorEmpleado`. |
| `src/domain/notificacionService.js` | Caso de uso: valida el evento, decide si guardarlo, hace el `console.log`. |
| `src/messaging/rabbitConsumer.js` | Conexión a RabbitMQ: declara el exchange (con los mismos parámetros que el productor), liga una cola propia y durable, reintenta si el broker no está listo, política de ack/nack (ver §6). |
| `src/api/notificacionesRouter.js` | `GET /notificaciones`, `GET /notificaciones/{empleadoId}`, con anotaciones `@openapi` para Swagger. |
| `src/api/healthRouter.js` | `GET /health`, `GET /health/ready`. |
| `src/docs/swagger.js` | Genera el documento OpenAPI a partir de los comentarios de las rutas. |
| `src/app.js` | Construye la app de Express sin arrancarla ni tocar RabbitMQ (para poder probarla con Supertest). |
| `src/server.js` | Punto de entrada: arranca `app.js`, el consumidor de RabbitMQ, y el apagado ordenado (`SIGTERM`/`SIGINT`). |
| `init.sql` | Esquema de la tabla `notificaciones` (mismo patrón que `database/registro/001-schema.sql` y `departamentos-serviceReto2/init.sql`: lo ejecuta Postgres solo, la primera vez que crea el volumen). |
| `Dockerfile`, `.dockerignore` | Build multi-stage, usuario sin privilegios (UID 10001), mismo patrón que los demás `Dockerfile` del repositorio. |
| `test/*.test.js` | 22 pruebas con Jest + Supertest (detalladas en el `README.md` del servicio). |

### Cambios fuera de la carpeta del servicio

| Archivo | Cambio |
|---|---|
| `docker-compose.yml` | Dos bloques nuevos: `notificaciones-db` y `notificaciones-service`, más el volumen `notificaciones-data`. También la sección de `registro-service` (ver §7). |
| `.env.example` | Variables de la base de datos de `notificaciones-service` (mismo patrón que las de `REGISTRO_DB_*`/`DEPARTAMENTOS_DB_*` que ya estaban). |

`notificaciones-service` publica su puerto directamente al host (`ports`, no
`expose`), sin pasar por el Gateway.

## 5. Contrato del evento (de dónde sale, qué se asume)

No hay un catálogo de eventos aparte en el repositorio; la única fuente de verdad es
el código de `empleados-service`
(`Reto-1/RegistroService/RegistroService/Infrastructure/Messaging/EventEnvelope.cs` y
`RabbitMqPublisher.cs`). De ahí:

```json
{
  "Id": "<guid>",
  "Type": "empleado.creado",
  "Version": "1.0",
  "OccurredAt": "2026-...",
  "Producer": "empleados-service",
  "Data": { "Id": "E001", "Nombre": "Juan", "Apellido": "Pérez", "Cargo": "Dev", "..." }
}
```

`RabbitMqPublisher.Publish` llama a `JsonSerializer.Serialize(envelope)` **sin**
opciones personalizadas, así que las claves salen en PascalCase (el nombre exacto de
la propiedad de C#), no en camelCase, y un `enum` como `Estado` saldría como número
(`0`, `1`, ...), no como texto. Se verificó leyendo el código fuente; no se pudo
confirmar de forma empírica con un mensaje real porque Docker Desktop estuvo caído de
forma intermitente durante el desarrollo (ver §8).

Por eso `eventoParser.js` busca cada campo probando varias formas de escribirlo
(`Id`/`id`, `Data`/`data`, etc.) en vez de asumir una sola. Es más barato tolerar
las dos formas que depender de un detalle de serialización de un servicio que no
se controla y que podría cambiar sin aviso.

`vacaciones.programadas` todavía no tiene productor en el repositorio (lo publicará
`vacaciones-service`, Integrante 4). Se diseñó el parseo para degradar con gracia si
faltan campos que no son esenciales (fechas), y se documenta aquí la forma que se
asumió para el mensaje simulado: `{ EmpleadoId, FechaInicio, FechaFin }` (o sus
variantes en minúscula). Si el evento real sale con otra forma, solo hay que ajustar
`construirMensaje()` en `eventoParser.js`; el resto del servicio (deduplicación,
persistencia, endpoints) no depende de esa forma.

## 6. Decisiones de la capa de mensajería

### 6.1 Cola propia y durable, ligada al exchange fanout existente

`empleados_exchange` es `fanout` y ya lo declara el productor: todo lo que se publica
ahí llega a **todas** las colas ligadas, sin importar el tipo de evento. Se declaró la
misma cola para ambos eventos (`empleado.creado` y `vacaciones.programadas`) porque no
hay manera de pedirle al fanout que filtre por tipo — el filtro pasa por `Type` dentro
del mensaje, en `notificacionService.procesarEvento`, ignorando sin error los tipos
que no interesan (`empleado.actualizado`, `empleado.retirado`, que también viajan por
el mismo exchange para `perfiles-service`).

La cola es durable y con nombre fijo (`notificaciones.empleados`), no exclusiva ni
auto-delete: así sobrevive a un reinicio de `notificaciones-service` sin perder los
eventos que hayan llegado mientras estaba caído — siempre que la cola ya existiera
antes de que se publicara el evento (si la cola no existe, un fanout simplemente no
tiene dónde entregar ese mensaje; por eso importa que `notificaciones-service` se
declare y ligue la cola tan pronto arranca, no que esté "siempre corriendo" para no
perder nada).

### 6.2 Deduplicación por `evento_id`, no por contenido

RabbitMQ garantiza entrega **at-least-once**, nunca *exactly-once*: si este servicio
se cae justo después de procesar un mensaje pero antes de confirmar el `ack`, RabbitMQ
lo vuelve a entregar. El campo `Id` del *envelope* (no el id del empleado) identifica
al evento, no al empleado, así que es la clave natural de idempotencia: un
`UNIQUE (evento_id)` en la base más `INSERT ... ON CONFLICT DO NOTHING` hace que
reprocesar el mismo mensaje sea un no-op, sin necesitar una tabla ni una lógica de
deduplicación aparte.

### 6.3 Cola de mensajes muertos (dead-letter) para eventos mal formados

Un mensaje que no es JSON válido, o que no trae los campos mínimos (`Id`, `Type`,
`Data.Id`), nunca va a "arreglarse solo" si se reintenta. Reencolarlo indefinidamente
crearía un bucle de redelivery infinito que además re-loguea el mismo error sin parar.
Por eso la cola principal se declara con `x-dead-letter-exchange`/
`x-dead-letter-routing-key` apuntando a `notificaciones.empleados.dlq`, y
`rabbitConsumer.js` hace `nack(mensaje, false, false)` (sin *requeue*) para esos casos
específicos, lo que RabbitMQ traduce automáticamente en "mándalo a la DLQ". Un error
transitorio (Postgres caído, por ejemplo) sí se reencola (`nack` con *requeue*),
porque ese sí puede resolverse solo cuando la base vuelva.

### 6.4 El consumidor arranca en paralelo al servidor HTTP, no antes

Si RabbitMQ tarda en levantar (o el healthcheck de Compose todavía no lo marcó
`healthy`), `notificaciones-service` igual debe poder responder `GET /notificaciones`
contra lo que ya tiene guardado. `rabbitConsumer.iniciar()` reintenta la conexión con
una espera fija en un bucle propio, en paralelo a que Express ya esté escuchando; no
bloquea el arranque del servidor HTTP.

## 7. Cambios fuera de esta carpeta

El único archivo compartido que se modificó, además de `docker-compose.yml` y
`.env.example` (ambos solo con líneas agregadas, ver §4), es la sección de
`registro-service` dentro de `docker-compose.yml`: se le agregaron las variables
`RabbitMQ__Host: message-broker`, `RabbitMQ__Port`, `RabbitMQ__Username` y
`RabbitMQ__Password`, y se agregó `message-broker` a su `depends_on` (con
`condition: service_started`, porque `message-broker` no tiene healthcheck en este
compose).

Esas variables son las que `RabbitMqPublisher.cs` (en `empleados-service`) necesita
para conectarse al broker; sin ellas usa por defecto `"localhost"`, que dentro del
propio contenedor de `registro-service` no llega a `message-broker`. Es la
configuración que hace posible que `empleado.creado` le llegue a este servicio (y a
cualquier otro consumidor) cuando todo corre junto con Docker Compose. No se modificó
ninguna línea de código C#, solo configuración de despliegue, y se coordinó con el
responsable de `empleados-service` (Integrante 1) antes de aplicarla.

`gateway-service` y la definición de `message-broker` no se modificaron.

## 8. Verificación

- **Pruebas automáticas:** `npm test` corre 22 pruebas (Jest + Supertest) contra la
  lógica de dominio y los endpoints HTTP, con un repositorio y un servicio falsos en
  memoria — no requieren Postgres ni RabbitMQ. Todas pasan.
- **Sin verificar con Docker Compose de punta a punta:** no se pudo levantar el stack
  completo en esta sesión (Docker Desktop estuvo caído de forma intermitente). Con el
  fix de §7 ya aplicado, lo que queda pendiente de verificar en cuanto Docker esté
  disponible es el camino completo `POST /empleados` → RabbitMQ → `notificaciones-service`:
  registrar un empleado y confirmar que la notificación aparece en `GET /notificaciones`
  y en `GET /notificaciones/{empleadoId}`. Mientras tanto, el consumidor se puede
  probar aparte publicando un mensaje de prueba directamente al exchange
  `empleados_exchange` (por ejemplo desde la consola de administración de RabbitMQ en
  `:15672`) con la forma de un envelope real.

## 9. Limitaciones conocidas

- `vacaciones.programadas` se consume con una forma de datos asumida (§5), porque su
  productor todavía no existe en el repositorio. Si `vacaciones-service` lo publica
  con campos distintos, solo hace falta ajustar `construirMensaje()`.
- El estado de la cola y la deduplicación viven en RabbitMQ y Postgres, no en memoria
  del proceso: con varias réplicas de `notificaciones-service`, todas comparten la
  misma cola (RabbitMQ reparte los mensajes entre los consumidores conectados) y la
  misma restricción `UNIQUE`, así que la deduplicación sigue siendo correcta con más
  de una réplica — no se probó ese escenario específicamente.
- No se implementó reintento con backoff exponencial para los `nack` con *requeue*
  (a diferencia del *circuit breaker* del Reto 3): RabbitMQ simplemente reentrega el
  mensaje de inmediato. Con una caída larga de Postgres esto genera reintentos
  constantes hasta que la base vuelva; para el alcance de este reto no se consideró
  necesario más que eso, pero sería la siguiente mejora natural.
