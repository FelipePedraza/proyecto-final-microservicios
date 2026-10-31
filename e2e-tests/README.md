# e2e-tests — Pruebas BDD de sistema completo

Módulo Maven independiente (Java 21, Cucumber 7, JUnit Platform, Rest Assured). No depende de ningún servicio:
habla con el sistema solo por HTTP a través del API Gateway (`http://localhost:8088`).

## Estructura

```text
e2e-tests/
├── pom.xml
├── .env.example                      variables de entorno (copiar y exportar)
└── src/test
    ├── java
    │   ├── config/E2EConfig.java     lectura de configuración: env > -De2e.xxx > defaults no sensibles
    │   ├── context/TestContext.java  estado compartido por escenario (JWT, última respuesta, ids creados)
    │   ├── hooks/Hooks.java          @Before / @After: cliente HTTP, limpieza
    │   ├── steps/                    step definitions (una clase por tema: SmokeSteps, ...)
    │   └── runners/RunCucumberTest.java
    └── resources
        ├── junit-platform.properties       plugins de reporte (HTML, JSON, JUnit XML)
        └── features/                 archivos .feature en español
```

## Cómo ejecutar

1. Levantar el sistema desde la raíz del repo: `docker compose up -d --build` y esperar a que todo esté `healthy`.
2. Desde `e2e-tests/`:

```bash
mvn clean test                             # todos los escenarios
mvn test -Dcucumber.filter.tags="@smoke"   # solo el smoke test
```

`mvn` termina con código de salida distinto de 0 si algún escenario falla (apto para CI).

Reportes en la carpeta estándar `reports/` (se sobrescriben en cada ejecución):

| Formato | Ruta |
|---|---|
| JUnit XML | `reports/junit.xml` |
| Cucumber JSON | `reports/cucumber.json` |
| Cucumber HTML | `reports/cucumber.html` |

Además, Surefire deja su resumen en `target/surefire-reports/`.

## Variables

| Variable | Por defecto | Uso |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:8088` | URL del gateway |
| `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD` | Email de ejemplo / requerido | Login ADMIN en escenarios que lo necesiten; la contraseña no tiene valor por defecto |
| `E2E_USER_EMAIL` / `E2E_USER_PASSWORD` | vacío | Opcional. Si están vacías, la suite crea una cuenta USER efímera por el flujo de onboarding |
| `E2E_STARTUP_TIMEOUT_SECONDS` | `120` | Espera máxima (arranque en frío) a que el gateway y sus rutas respondan |
| `E2E_REQUEST_TIMEOUT_MS` | `30000` | Timeout por petición |
| `E2E_POLLING_MAX_ATTEMPTS` | `15` | Número máximo de consultas de una condición asincrónica |
| `E2E_POLLING_INTERVAL_MS` | `2000` | Intervalo entre consultas de polling |
| `E2E_POLLING_TIMEOUT_SECONDS` | `90` | Límite total de una espera asincrónica |
| `E2E_JWT_SECRET` | vacío | Mismo valor de `JWT_SECRET_KEY`; permite activar las cuentas efímeras de onboarding |

## Polling y consistencia eventual

Los pasos que comienzan con `eventualmente` usan la utilidad compartida `support/Polling.java`. La
condición se consulta inmediatamente y luego cada 2 segundos, hasta un máximo de 15 intentos o 90
segundos. Los 2 s bastan para detectar el evento poco después de procesarse; el tope de 90 s cubre
una petición lenta en arranque en frío (el timeout por petición es de 30 s) más reintentos, sin que
una sola respuesta lenta agote la espera. La prueba termina tan pronto como el evento es procesado y se
tolera la variación normal de RabbitMQ y sus consumidores. Los valores pueden
ajustarse mediante las variables anteriores sin recompilar la suite. Si se agota el límite, el paso
falla con el número de intentos y la condición que no se cumplió.

El historial de notificaciones oculta deliberadamente los tokens de activación. Por ello, la suite
espera primero la notificación `SEGURIDAD` que confirma que `auth-service` procesó el evento y luego
emite un token temporal E2E firmado con `E2E_JWT_SECRET`. Ese token solo se utiliza contra la cuenta
efímera creada por el escenario y evita consultar directamente las bases de datos.

## Independencia de escenarios

- PicoContainer construye un `TestContext` nuevo para cada escenario; JWT, respuestas e IDs no se
  comparten entre escenarios.
- Cada empleado usa un ID, número y correo derivados de un UUID. Por ello, una ejecución anterior no
  puede provocar conflictos de unicidad en otra.
- Los `Antecedentes` recrean desde cero las precondiciones complejas. En vacaciones y offboarding se
  registra el empleado, se espera su cuenta asincrónica, se establece la contraseña y se comprueba el
  login antes de comenzar cada escenario.
- Toda creación exitosa registra una acción `onCleanup`. Como empleados solo ofrece baja lógica, el
  hook `@After` los deja `RETIRADO`; los UUID evitan reutilizarlos. Los departamentos de fixtures usan
  IDs estables y aceptan `409` si ya existen, evitando acumular un departamento por escenario.
- La limpieza se ejecuta en orden inverso al setup y sus fallos quedan registrados en el reporte sin
  ocultar el resultado funcional del escenario.

## Prerrequisitos del sistema bajo prueba

- Stack levantado con `docker compose up -d --build` y contenedores `healthy`.
- `VACACIONES_MANUAL_ENABLED=true` en el `.env` raíz: el ciclo de vacaciones usa `POST /vacaciones/{id}/forzar-inicio` y
  `forzar-fin` en lugar de esperar al cron del scheduler (sin la bandera esos endpoints no existen y el escenario falla).
- Las fechas de los escenarios se calculan en UTC, igual que el reloj de `vacaciones-service`.

## Cómo añadir escenarios (David, Samuel)

- Pon el `.feature` en `src/test/resources/features/` con `# language: es` y una etiqueta propia (`@seguridad`, `@onboarding`, `@offboarding`...).
- Pon los steps en una clase nueva dentro de `steps/` que reciba `TestContext` por constructor. No modifiques `TestContext`, `Hooks` ni `E2EConfig`
  sin avisar a Daniel: son el contrato compartido. Si necesitas un dato nuevo, usa `context.put(...)` / `context.registerCreated(...)`.
- Registra con `context.onCleanup(...)` lo que el escenario cree para que el hook `@After` lo limpie.
