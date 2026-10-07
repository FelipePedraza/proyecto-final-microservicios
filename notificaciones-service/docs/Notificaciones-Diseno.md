# notificaciones-service — Qué se hizo, en qué archivos y por qué

Reto 4, responsabilidad de **Integrante 2**. Este documento sigue el mismo formato que
[`Reto3-CircuitBreaker.md`](../../Reto-1/RegistroService/RegistroService/DOC/Reto3-CircuitBreaker.md):
qué se creó, dónde, y el razonamiento detrás de cada decisión.

## 1. Objetivo y alcance

| Requisito | Resultado |
|-----------|-----------|
| Servicio y base de datos propios | `notificaciones-service` (Node/Express) + `notificaciones-db` (Postgres), ambos nuevos en `docker-compose.yml` |
| Consumir `empleado.creado`, `empleado.retirado` y `vacaciones.programadas` | Consumidor de RabbitMQ ligado al exchange fanout `empleados_exchange` |
| Generar logs simulando notificaciones | Log `[NOTIFICACIÓN] Tipo: ... | Para: ... | Mensaje: ...` por cada notificación procesada |
| Guardar historial | Tabla `notificaciones`; la API expone `id`, `tipo`, `destinatario`, `mensaje`, `fechaEnvio` y `empleadoId` |
| `GET /notificaciones` y `GET /notificaciones/{empleadoId}` | Implementados |
| Deduplicación por id del evento | `UNIQUE (evento_id)` + `ON CONFLICT DO NOTHING` |
| Dockerfile + Swagger/OpenAPI | Implementados |

También existe `perfiles-service` (Integrante 3), que consume `empleado.actualizado` y
`empleado.retirado` para mantener su propio historial de perfiles. El evento de retiro
lo procesan ambos servicios con propósitos distintos: perfiles archiva el estado y
notificaciones guarda el aviso en el historial consultable. `vacaciones-service`
(Integrante 4) publica `vacaciones.programadas` e integra `/vacaciones` al Gateway. No se
creó ni se tocó código de ninguno de esos servicios.

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
| `test/*.test.js` | 26 pruebas con Jest + Supertest (detalladas en el `README.md` del servicio). |

### Cambios fuera de la carpeta del servicio

| Archivo | Cambio |
|---|---|
| `docker-compose.yml` | Dos bloques nuevos: `notificaciones-db` y `notificaciones-service`, más el volumen `notificaciones-data`. También la sección de `registro-service` (ver §7). |
| `.env.example` | Variables de la base de datos de `notificaciones-service` (mismo patrón que las de `REGISTRO_DB_*`/`DEPARTAMENTOS_DB_*` que ya estaban). |

En Docker Compose, `notificaciones-service` escucha en el puerto interno `8082` y se
expone únicamente dentro de `microservicios-network`. El Gateway lo publica hacia el
host mediante `http://localhost:8088/notificaciones`; el mapeo directo `8082:8082` del
Dockerfile corresponde solo a una ejecución aislada del contenedor.

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

`vacaciones.programadas` lo publica `vacaciones-service` con `empleadoId`,
`fechaInicio` y `fechaFin`; ese evento no incluye el correo del empleado. Para
registrar el destinatario sin alterar el contrato del evento, Notificaciones lo
resuelve desde el payload de `empleado.creado` que ya conserva en el historial.
El parseo tolera PascalCase/camelCase y degrada con gracia si faltan fechas.

## 6. Decisiones de la capa de mensajería

### 6.1 Cola propia y durable, ligada al exchange fanout existente

`empleados_exchange` es `fanout` y ya lo declara el productor: todo lo que se publica
ahí llega a **todas** las colas ligadas, sin importar el tipo de evento. Se declaró la
misma cola para los tres tipos que procesa este servicio (`empleado.creado`,
`empleado.retirado` y `vacaciones.programadas`) porque no hay manera de pedirle al
fanout que filtre por tipo — el filtro pasa por `Type` dentro
del mensaje, en `notificacionService.procesarEvento`, ignorando sin error los tipos
que no interesan (por ejemplo, `empleado.actualizado`). `empleado.retirado` sí se procesa
aquí para guardar la notificación; `perfiles-service` lo consume en paralelo para
archivar el perfil.

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

Los cambios de integración fuera de esta carpeta se hicieron en `docker-compose.yml`,
`.env.example` y la configuración de rutas de `gateway-service`. En Compose se agregó
la sección de `registro-service`, a la que se le agregaron las variables
`RABBITMQ_HOST=message-broker`, `RABBITMQ_PORT`, `RABBITMQ_USER` y
`RABBITMQ_PASSWORD`, y se agregó `message-broker` a su `depends_on` (con
`condition: service_started`, porque `message-broker` no tiene healthcheck en este
compose).

Esas variables son las que `RabbitMqPublisher.cs` (en `empleados-service`) necesita
para conectarse al broker; sin ellas usa por defecto `"localhost"`, que dentro del
propio contenedor de `registro-service` no llega a `message-broker`. Es la
configuración que hace posible que `empleado.creado` le llegue a este servicio (y a
cualquier otro consumidor) cuando todo corre junto con Docker Compose. No se modificó
ninguna línea de código C#, solo configuración de despliegue, y se coordinó con el
responsable de `empleados-service` (Integrante 1) antes de aplicarla.

`gateway-service` ahora incluye una ruta `/notificaciones/**` hacia
`http://notificaciones-service:8082`, con su propio Circuit Breaker. La definición de
`message-broker` no se modificó.

## 8. Verificación

- **Pruebas automáticas:** `npm test` corre 26 pruebas (Jest + Supertest) contra la
  lógica de dominio y los endpoints HTTP, con un repositorio y un servicio falsos en
        memoria — no requieren Postgres ni RabbitMQ. En esta sesión no se pudo ejecutar la
        suite porque `jest` no está instalado; el flujo de desvinculación se validó con una
        comprobación funcional directa en Node.
- **Deduplicación manual:** la publicación repetida del mismo evento desde la UI de
        RabbitMQ se realizó y confirmó un único registro, según la verificación del equipo.
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

- `vacaciones.programadas` no incluye el correo del empleado; cuando no se encuentra
        un evento `empleado.creado` previo para resolverlo, el destinatario se registra como
        `no informado`.
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
