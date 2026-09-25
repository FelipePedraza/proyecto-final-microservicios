CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE SEQUENCE IF NOT EXISTS vacaciones_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS vacaciones (
    id VARCHAR(20) PRIMARY KEY,
    empleado_id VARCHAR(100) NOT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('PROGRAMADA', 'EN_CURSO', 'FINALIZADA', 'CANCELADA')),
    fecha_creacion TIMESTAMPTZ NOT NULL,
    CONSTRAINT ex_vacaciones_sin_solape
      EXCLUDE USING gist (
        empleado_id WITH =,
        daterange(fecha_inicio, fecha_fin, '[]') WITH &&
      )
      WHERE (estado IN ('PROGRAMADA', 'EN_CURSO'))
);

CREATE INDEX IF NOT EXISTS ix_vacaciones_empleado_estado ON vacaciones (empleado_id, estado);
CREATE INDEX IF NOT EXISTS ix_vacaciones_empleado_fechas ON vacaciones (empleado_id, fecha_inicio, fecha_fin);

CREATE TABLE IF NOT EXISTS empleados_validos (
    id VARCHAR(100) PRIMARY KEY,
    nombre VARCHAR(200),
    email VARCHAR(320),
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('ACTIVO', 'RETIRADO')),
    actualizado_en TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS eventos_procesados (
    id VARCHAR(255) PRIMARY KEY,
    procesado_en TIMESTAMPTZ NOT NULL
);
