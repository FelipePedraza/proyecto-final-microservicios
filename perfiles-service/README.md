# perfiles-service

Servicio de perfiles de empleados implementado en Go 1.22, con PostgreSQL 17 y RabbitMQ (`amqp091-go`).

Consume el exchange fanout `empleados_exchange` en la cola durable `perfiles.empleados` y su DLQ. Acepta envelopes PascalCase y camelCase. Los eventos `empleado.creado`, `empleado.actualizado` y `empleado.retirado` crean/sincronizan/archivan perfiles. La tabla `eventos_procesados` deduplica por el `id` del envelope dentro de la misma transacción.

Endpoints internos (expuestos por el Gateway):

- `GET /perfiles`
- `GET /perfiles/{empleadoId}`
- `PUT /perfiles/{empleadoId}` con `telefono`, `direccion`, `ciudad` y `biografia`
- `GET /health/live`, `GET /health/ready`
- Swagger UI: `/swagger/index.html`

Prueba: `docker compose up --build`, crea un empleado a través de `/empleados`, consulta `/perfiles/E001`, actualiza campos con `PUT` y repite el mismo mensaje en RabbitMQ. El segundo mensaje se registra como duplicado y no modifica el perfil.
