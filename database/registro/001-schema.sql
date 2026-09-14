CREATE TABLE IF NOT EXISTS "Empleados" (
    "Id" VARCHAR(100) NOT NULL,
    "Nombre" VARCHAR(150) NOT NULL,
    "Apellido" VARCHAR(150) NOT NULL,
    "Email" VARCHAR(254) NOT NULL,
    "NumeroEmpleado" VARCHAR(100) NOT NULL,
    "Cargo" VARCHAR(150) NOT NULL,
    "Area" VARCHAR(150) NOT NULL,
    "DepartamentoId" VARCHAR(100) NOT NULL,
    "FechaIngreso" DATE NOT NULL,
    "Estado" VARCHAR(30) NOT NULL,
    CONSTRAINT "PK_Empleados" PRIMARY KEY ("Id")
);

CREATE UNIQUE INDEX IF NOT EXISTS "IX_Empleados_Email"
    ON "Empleados" ("Email");

CREATE UNIQUE INDEX IF NOT EXISTS "IX_Empleados_NumeroEmpleado"
    ON "Empleados" ("NumeroEmpleado");
