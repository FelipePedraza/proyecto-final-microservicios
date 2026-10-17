# Reto 7: Integración Continua (CI) con Jenkins y SonarQube

## 1. Contexto y Visión General
Este reto complementa la arquitectura del sistema de onboarding y offboarding de empleados, evolucionando los avances previos (microservicios REST, eventos asincrónicos, seguridad JWT/RBAC y pirámide de pruebas automatizadas con BDD) hacia la **Integración Continua (CI)**.

### El Problema
- Compilaciones, pruebas y empaquetados Docker realizados manualmente en cada máquina.
- Detección tardía de integración de cambios que rompen el sistema.
- Reportes del Reto 6 aislados localmente (XML/HTML) sin visibilidad colectiva, análisis de tendencias ni seguimiento de cobertura entre commits.

### La Solución
Implementar un pipeline de CI automatizado con **Jenkins**, **SonarQube** y **Docker**, garantizando que ante cada cambio:
1. El código se compile y ejecute sus pruebas automáticamente.
2. Los reportes de pruebas (unitarias, integración y BDD) y cobertura se publiquen visualmente en Jenkins.
3. Se analice la calidad del código en SonarQube y se verifique el Quality Gate ($\ge70\%$ cobertura).
4. Se construyan las imágenes Docker del servicio.
5. Se ejecuten las pruebas End-to-End (E2E/BDD) contra el sistema completo.

---

## 2. Mapa de Arquitectura del Pipeline

```
[ Git Push / Trigger ]
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│                    JENKINS PIPELINE                     │
│                                                         │
│  1. Checkout  ──► 2. Build ──► 3. Test & Coverage       │
│                                         │               │
│                                         ▼               │
│  5. Package   ◄── 4. Quality Gate ◄── SonarQube Analysis│
│        │          (Coverage ≥70%)                       │
│        ▼                                                │
│  6. E2E Tests (BDD en Docker Compose)                   │
│        │                                                │
│        ▼                                                │
│  7. Publicación de Reportes y Cleanup                   │
└─────────────────────────────────────────────────────────┘
```

---

## 3. Desglose de los 5 Puntos del Reto

| # | Punto | Objetivo Principal | Entregables Clave |
|---|---|---|---|
| **1** | **Configuración de Jenkins en Docker** | Levantar Jenkins reproducible con plugins pre-instalados y acceso a Docker. | `jenkins/Dockerfile`, `docker-compose.yml` (servicio Jenkins), pipeline de verificación con `docker --version`. |
| **2** | **Primer Jenkinsfile & Reportes** | Compilar, probar y publicar reportes de tests y cobertura. | `Jenkinsfile` declarativo, reportes JUnit + Cobertura con `recordCoverage` en `post { always { } }`, provisionamiento de jobs as Code (JCasC o init scripts). |
| **3** | **Calidad con SonarQube** | Inspección estática del código y control de Quality Gate. | SonarQube + Postgres en Compose, etapa SonarQube en `Jenkinsfile`, Quality Gate personalizado ($\ge 70\%$ cobertura). |
| **4** | **Empaquetado Docker & E2E BDD** | Construir imagen, ejecutar pruebas de extremo a extremo y publicar reportes BDD navegables. | Etapa `Package`, ejecución BDD en Compose, publicación con `cucumber-reports` / `htmlpublisher`, archivado de logs, mínimo 2 Jenkinsfiles (2 lenguajes). |
| **5** | **Reproducibilidad & Documentación** | Garantizar ejecuciones estables, simulación de fallos y documentación completa. | Pruebas de 3 ejecuciones seguidas, prueba de 4 fallos simulados, `README.md` detallado con capturas de pantalla y tabla de reportes. |

---

## 4. Tabla Resumen de Publicación de Reportes

| Reporte | Formato Origen | Comando / Plugin de Publicación | Ubicación / Pestaña en Jenkins |
|---|---|---|---|
| **Pruebas Unitarias / Integración** | JUnit XML (`*.xml`) | `junit '**/target/*-reports/*.xml'` | Pestaña **Test Result** con tendencia |
| **Cobertura de Código** | JaCoCo XML / Cobertura XML | `recordCoverage(tools: [[parser: 'JACOCO', pattern: '...']])` | Pestaña **Coverage** con % y tendencia |
| **Escenarios BDD** | Cucumber JSON (`cucumber.json`) | `cucumber(...)` | Pestaña **Cucumber Reports** (en español) |
| **Reportes HTML Genericos** | HTML estático (`*.html`) | `publishHTML(...)` | Menú lateral del Build **Reporte BDD** |
| **Logs de Sistema (E2E)** | Text log (`docker-logs.txt`) | `archiveArtifacts artifacts: 'docker-logs.txt'` | Sección **Build Artifacts** |

---

## 5. Criterios Importantes de Evaluación
- **Idempotencia:** Ejecutar el pipeline N veces seguidas debe dar el mismo resultado.
- **Manejo de Errores:** Se usa `-Dmaven.test.failure.ignore=true` (o `|| true`) en la etapa de tests para garantizar que los reportes siempre se publiquen en el bloque `post` antes de evaluar el fallo.
- **Limpieza (Cleanup):** La etapa E2E siempre ejecuta `docker compose down -v` al terminar para no contaminar ejecuciones futuras.
- **Sin intervención manual:** Plugins, Jobs y dependencias deben aprovisionarse por código (JCasC/Dockerfile).