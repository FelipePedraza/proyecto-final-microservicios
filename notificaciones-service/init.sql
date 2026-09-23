-- Esquema de notificaciones-service.
-- Se ejecuta automaticamente por la imagen oficial de Postgres la PRIMERA vez que crea el
-- volumen (carpeta /docker-entrypoint-initdb.d), igual que database/registro/001-schema.sql
-- y departamentos-serviceReto2/init.sql. Es la unica fuente de verdad de este esquema:
-- no hay migraciones ni ORM en este servicio.

CREATE TABLE IF NOT EXISTS notificaciones (
    id            BIGSERIAL PRIMARY KEY,

    -- Id del evento (campo "Id" del EventEnvelope publicado por empleados-service).
    -- Es la clave de deduplicacion: RabbitMQ garantiza entrega "at-least-once", asi que
    -- el mismo mensaje puede llegar mas de una vez (por ejemplo si este servicio se cae
    -- despues de procesar pero antes de confirmar el ack). El UNIQUE aqui es lo que hace
    -- que reprocesar el mismo evento_id sea un no-op en vez de una notificacion duplicada.
    evento_id     VARCHAR(100) NOT NULL,

    -- Tipo del evento ("empleado.creado", "vacaciones.programadas", ...).
    tipo_evento   VARCHAR(100) NOT NULL,

    -- Id del empleado al que se refiere la notificacion (extraido de Data.Id del evento).
    empleado_id   VARCHAR(100) NOT NULL,

    -- Texto de la notificacion simulada (lo que un canal real -email, SMS- habria enviado).
    mensaje       TEXT NOT NULL,

    -- Payload crudo del evento, tal como llego (para auditoria/depuracion si el formato
    -- cambia o si hace falta reconstruir el detalle mas adelante).
    payload       JSONB NOT NULL,

    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_notificaciones_evento_id UNIQUE (evento_id)
);

-- GET /notificaciones/{empleadoId} filtra por empleado_id; este indice evita un seq scan
-- a medida que crece el historial.
CREATE INDEX IF NOT EXISTS ix_notificaciones_empleado_id
    ON notificaciones (empleado_id);

-- GET /notificaciones lista mas recientes primero.
CREATE INDEX IF NOT EXISTS ix_notificaciones_creado_en
    ON notificaciones (creado_en DESC);
