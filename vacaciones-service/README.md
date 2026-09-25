# Vacaciones Service

Servicio Java 21/Spring Boot 4 para registrar, consultar y cancelar vacaciones.

* `POST /vacaciones` crea una solicitud; las fechas son inclusivas y no pueden
  solaparse con otra vacación activa del empleado.
* `GET /vacaciones/{id}` y `GET /vacaciones?empleadoId=...` consultan vacaciones.
* `DELETE /vacaciones/{id}` cancela únicamente períodos `PROGRAMADA`.
* Salud: `/health` y `/health/ready`; OpenAPI: `/vacaciones/docs` y
  `/vacaciones/api-docs`.

Configurar `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` y las
variables `RABBITMQ_*`. La cola durable `vacaciones.empleados` usa
`vacaciones.empleados.dlq`; el consumidor acepta `id`/`Id`, `type`/`Type` y
`empleadoId`/`EmpleadoId`. La carga de `vacaciones.programadas`
(`vacacionId`, `empleadoId`, `fechaInicio`, `fechaFin`) es un supuesto
**pendiente de verificar contra el catálogo**.
