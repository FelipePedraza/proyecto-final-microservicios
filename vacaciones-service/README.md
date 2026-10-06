# Vacaciones Service

Servicio Java 21/Spring Boot 4 para registrar, consultar y cancelar vacaciones,
con transiciones automáticas y eventos de ciclo de vida.

## API

* `POST /vacaciones` crea una solicitud. Las fechas son inclusivas: se permite
  que `fechaInicio` y `fechaFin` sean el mismo día. No se permiten fechas de
  inicio pasadas ni solapamientos con otro período activo del empleado.
* `GET /vacaciones/{id}` y `GET /vacaciones?empleadoId=...` consultan períodos.
* `DELETE /vacaciones/{id}` cancela únicamente períodos `PROGRAMADA`.
* `GET /health` y `/health/ready` exponen salud.
* OpenAPI: `/v3/api-docs`; Swagger UI: `/swagger-ui.html` (interfaz en
  `/swagger-ui/index.html`). Ambas rutas se sirven desde la raíz del servicio.
  La especificación incluye el esquema bearer JWT que aplica el API Gateway a
  las operaciones de negocio; requiere el rol `ADMIN` para escrituras. Salud y
  documentación son públicas. Los endpoints manuales aparecen solo cuando
  `VACACIONES_MANUAL_ENABLED=true`.

## Scheduler y eventos

`VacationScheduler` se ejecuta mediante Spring `@Scheduled`; el valor
`VACACIONES_CRON` usa la sintaxis cron de Spring con seis campos y por defecto
`0 * * * * *` (una vez por minuto). `VACACIONES_CRON` se puede definir en `.env`
o en el entorno del contenedor.

En cada ejecución se realizan las transiciones:

* `PROGRAMADA` con `fechaInicio <= hoy` pasa a `EN_CURSO` y publica
  `vacaciones.iniciadas`.
* `EN_CURSO` con `fechaFin < hoy` pasa a `FINALIZADA` y publica
  `vacaciones.finalizadas`. Como las fechas son inclusivas, un período que
  termina hoy sigue activo durante todo ese día.

Ambos eventos se publican en el exchange fanout `empleados_exchange` como
envelopes con `version: "1.0"` (string), `producer: "vacaciones-service"` y
`data` con `vacacionId`, `empleadoId`, `fechaInicio` y `fechaFin`. El evento se
emite después del commit y su contenido se captura al realizar la transición,
por lo que cada evento conserva su tipo aunque el mismo registro cambie otra
vez en esa transacción.

El retiro cancela solo períodos `PROGRAMADA`; un período `EN_CURSO` se conserva
y el scheduler publica `vacaciones.finalizadas` al terminar. No se comprueba el
estado del empleado para omitir ese evento: el `auth-service` debe ignorarlo
cuando la cuenta ya quedó permanentemente desactivada.

### Demostración rápida

Para demostrar los eventos sin esperar a que cambie la fecha, habilita de forma
explícita en `.env`:

```dotenv
VACACIONES_MANUAL_ENABLED=true
```

Recrea `vacaciones-service` (`docker compose up -d --build --force-recreate vacaciones-service`).
Con un JWT `ADMIN`, llama a `POST /vacaciones/{id}/forzar-inicio` y luego a
`POST /vacaciones/{id}/forzar-fin`. Ambos endpoints están deshabilitados por
defecto y solo se deben habilitar en desarrollo/demostraciones; el API Gateway
aplica su regla general de escritura, que exige `ADMIN`. La fecha de inicio y
fin puede ser hoy para crear rápidamente el período.

Para observar exclusivamente el scheduler real, crea una vacación que empiece
hoy y deja `VACACIONES_CRON=0 * * * * *`; el inicio se procesará en el siguiente
ciclo. Su fecha de fin debe quedar atrás para que el siguiente ciclo la finalice.

### Escalamiento

Esta implementación asume una instancia: `@Scheduled` corre en cada réplica,
así que escalar `vacaciones-service` a N instancias puede publicar cada evento
N veces. La deduplicación del consumidor mitiga efectos repetidos, pero no evita
el trabajo duplicado del productor. La coordinación distribuida con ShedLock
queda para el Reto 31.

## Configuración y pruebas

Configura `DB_*`, `RABBITMQ_*`, `VACACIONES_CRON` y
`VACACIONES_MANUAL_ENABLED`; Compose usa una base de datos PostgreSQL y el
exchange compartido. Ejecuta las pruebas del servicio desde la raíz:

```powershell
mvn -f vacaciones-service/pom.xml test
```

La colección de integración Postman y la secuencia completa de 18 escenarios se
documentan en [`docs/evidencias/reto5/README.md`](../docs/evidencias/reto5/README.md).
