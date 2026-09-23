-- Esquema de notificaciones-service.
-- Se ejecuta automaticamente por la imagen oficial de Postgres la PRIMERA vez que crea el
-- volumen (carpeta /docker-entrypoint-initdb.d), igual que database/registro/001-schema.sql
-- y database/departamentos/001-schema.sql. Es la unica fuente de verdad de este esquema:
-- no hay migraciones ni ORM en este servicio.

CREATE TABLE IF NOT EXISTS notificaciones (
    id            BIGSERIAL PRIMARY KEY,

    evento_id     VARCHAR(100) NOT NULL,
    tipo_evento   VARCHAR(100) NOT NULL,
    empleado_id   VARCHAR(100) NOT NULL,
    mensaje       TEXT NOT NULL,
    payload       JSONB NOT NULL,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_notificaciones_evento_id UNIQUE (evento_id)
);

CREATE INDEX IF NOT EXISTS ix_notificaciones_empleado_id
    ON notificaciones (empleado_id);

CREATE INDEX IF NOT EXISTS ix_notificaciones_creado_en
    ON notificaciones (creado_en DESC);
