CREATE TABLE IF NOT EXISTS perfiles (
    id UUID PRIMARY KEY,
    empleado_id VARCHAR(100) NOT NULL UNIQUE,
    nombre VARCHAR(200) NOT NULL,
    email VARCHAR(320) NOT NULL,
    telefono VARCHAR(50) NOT NULL DEFAULT '',
    direccion VARCHAR(300) NOT NULL DEFAULT '',
    ciudad VARCHAR(100) NOT NULL DEFAULT '',
    biografia TEXT NOT NULL DEFAULT '',
    archivado BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_archivado TIMESTAMPTZ NULL
);

CREATE TABLE IF NOT EXISTS eventos_procesados (
    id VARCHAR(255) PRIMARY KEY,
    procesado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
