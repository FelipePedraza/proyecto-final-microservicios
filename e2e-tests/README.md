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
    │   ├── config/E2EConfig.java     lectura de configuración: env > -De2e.xxx > defecto
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
mvn test                                   # todos los escenarios
mvn test -Dcucumber.filter.tags="@smoke"   # solo el smoke test
```

Reportes en `target/cucumber-reports/` (`cucumber.html`, `cucumber.json`, `cucumber.xml`) y `target/surefire-reports/`.

## Variables

| Variable | Por defecto | Uso |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:8088` | URL del gateway |
| `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD` | vacío | Login ADMIN en escenarios que lo necesiten |
| `E2E_STARTUP_TIMEOUT_SECONDS` | `60` | Espera máxima a que el gateway responda |
| `E2E_REQUEST_TIMEOUT_MS` | `30000` | Timeout por petición |

## Cómo añadir escenarios (David, Samuel)

- Pon el `.feature` en `src/test/resources/features/` con `# language: es` y una etiqueta propia (`@seguridad`, `@onboarding`, `@offboarding`...).
- Pon los steps en una clase nueva dentro de `steps/` que reciba `TestContext` por constructor. No modifiques `TestContext`, `Hooks` ni `E2EConfig`
  sin avisar a Daniel: son el contrato compartido. Si necesitas un dato nuevo, usa `context.put(...)` / `context.registerCreated(...)`.
- Registra con `context.onCleanup(...)` lo que el escenario cree para que el hook `@After` lo limpie.
