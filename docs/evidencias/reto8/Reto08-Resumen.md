# Reto 8 – Observabilidad y Monitoreo del Ecosistema de Microservicios

> **Curso:** Arquitectura Orientada a Microservicios - UniQuindío  
> **Prof:** Christian Andrés Candela Uribe

## 1. Contexto y Objetivo

El ecosistema actual (onboarding/offboarding) ya tiene Docker, comunicación sync/async, JWT, tests e CI, pero sigue siendo una caja negra.

**Objetivo:** Integrar un stack de observabilidad completo para responder:

- ¿Está vivo? → **Health Checks** (`/health`)
- ¿Cómo se comporta? → **Métricas** (Prometheus)
- ¿Qué hizo exactamente? → **Trazas distribuidas** (Zipkin/Jaeger + OpenTelemetry)
- ¿Me avisa si falla? → **Alertas proactivas** (Grafana)

## 2. Requisitos Generales

- Cada microservicio expone `/health` (JSON con estado de DB/broker) y `/metrics` en formato Prometheus.
- Todos instrumentados con **OpenTelemetry** (W3C Trace Context).
- Stack de observabilidad desplegado en `docker-compose.yml`.
- Mínimo 1 dashboard Grafana con métricas de varios servicios.
- Mínimo 2 reglas de alerta en Grafana con canal externo.
- Todo documentado en README + diagramado.

## 3. Investigación Previa (documentar en README)

| Concepto | Qué investigar |
| :--- | :--- |
| 3 pilares | Métricas, Logs, Trazas – rol de cada uno |
| Pull vs Push | Prometheus hace Pull (scrape), Zipkin/Jaeger Push de spans |
| OpenTelemetry | Qué es CNCF y por qué OTel es estándar agnóstico |
| W3C Trace Context | Cómo viaja `traceId` por headers HTTP entre lenguajes |

**Entregable:** Diagrama de arquitectura previo con: componentes observabilidad, cómo Prometheus scrapea por red Docker, flujo de trazas, cómo Grafana consume Prometheus.

## 4. Infraestructura Docker

Agregar al `docker-compose.yml` existente:

| Servicio | Imagen | Puerto | Rol |
| :--- | :--- | :--- | :--- |
| Prometheus | `prom/prometheus:latest` | 9090 | Recolección métricas Pull |
| Grafana | `grafana/grafana:latest` | 3000 | Visualización + alertas |
| Loki | `grafana/loki:latest` | 3100 | Agregación logs |
| Promtail | `grafana/promtail:latest` | - | Agente que envía logs a Loki |
| Zipkin o Jaeger | `openzipkin/zipkin:latest` | 9411 / 16686 | Trazas distribuidas |

> Elegir Zipkin o Jaeger y justificar en README.  
> Todos en la misma network que los microservicios. Usar nombres de servicio Docker, no localhost.  
> Grafana: datasource Prometheus aprovisionado automáticamente + volumen `grafana-data`.

### Estructura esperada
```
observability/
  prometheus/prometheus.yml
  grafana/provisioning/ (datasources + dashboards JSON)
  promtail/promtail-config.yml
docker-compose.yml
```

## 5. Configuración Prometheus (`prometheus.yml`)

- `scrape_interval: 15s`
- 1 job por microservicio (7 en total): `api-gateway` (PRIMERO), `empleados-service`, `departamentos-service`, `auth-service`, `perfiles-service`, `notificaciones-service`, `vacaciones-service`.
- `targets: ['nombre-servicio-docker:puerto']`
- `metrics_path` según stack: Spring `/actuator/prometheus`, Express/FastAPI/Go ` /metrics`.

## 6. Instrumentación de Microservicios

### 6.1 Métricas Prometheus
Librerías:
- Java: `micrometer-registry-prometheus`
- Node: `prom-client`
- Python FastAPI: `prometheus-fastapi-instrumentator`, Flask: `prometheus-flask-exporter`
- Go: `client_golang`, .NET: `prometheus-net.AspNetCore`

Métricas mínimas: CPU/memoria, `http_requests_total` por status, latencia `http_request_duration_seconds_*`.

### 6.2 Trazabilidad OpenTelemetry
Librerías por lenguaje: Spring `opentelemetry-spring-boot-starter`, Node `@opentelemetry/sdk-node + exporter-zipkin`, Python `opentelemetry-sdk + exporter-zipkin`, Go `go.opentelemetry.io/otel`.

Config mínima:
```
OTEL_SERVICE_NAME=empleados-service
OTEL_EXPORTER_ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans
OTEL_PROPAGATORS=tracecontext,baggage
```
Usar nombre Docker, no localhost.

**Caso crítico: vacaciones-service (scheduler)**
No hay petición HTTP que origine traza. Hay que:
1. Crear span raíz manual: `tracer.start_as_current_span("scheduler.vacaciones.iniciar")`
2. `set_attribute` de ids
3. Inyectar contexto en headers del mensaje: `TraceContextTextMapPropagator().inject(headers)`
4. En consumidor extraer contexto para colgar del mismo árbol.

Verificación: en Zipkin debe verse 1 sola cascada `scheduler -> auth-service -> notificaciones-service`, no 3 trazas sueltas.

### 6.3 Health Endpoint `GET /health`
```json
{ "status": "UP", "service": "empleados-service", "checks": { "database": "UP", "messageBroker": "UP" } }
```
`UP` → 200, `DOWN` → 503. Verificar conectividad real a DB y broker.

### 6.4 Logs Estructurados (Loki + Promtail)
Promtail necesita volumen `/var/run/docker.sock` y config:
```yaml
clients: [{ url: http://loki:3100/loki/api/v1/push }]
scrape_configs:
  docker_sd_configs + relabel container/service
```
Logs en JSON: `timestamp, level, service, traceId, message, employeeId`. El `traceId` viene de OTel y correlaciona log ↔ traza.

Librerías: Logback+logstash-encoder, winston json, structlog, zap/zerolog, Serilog.

## 7. Dashboard Grafana

Mínimo 4 paneles:

| Panel | PromQL | Tipo |
| :--- | :--- | :--- |
| Estado servicio | `up{job="nombre-servicio"}` | Stat verde/rojo |
| Tasa peticiones | `rate(http_requests_total[1m])` | Time Series |
| Latencia promedio | `rate(http_request_duration_seconds_sum[1m]) / rate(http_request_duration_seconds_count[1m])` | Time Series |
| Errores HTTP | `sum by (status) (rate(http_requests_total{status=~"[45].."}[1m]))` | Time Series |

Organización: Row 1 Resumen Sistema (Stats), Row 2 Comportamiento Tráfico (series). Adaptar nombres métricas según librería.

## 8. Alertas Proactivas (Grafana UI)

Mínimo 2 reglas:

1.  **Servicio Caído:** `up{job="nombre-servicio"} == 0 durante 1 min` → servicio no responde a Prometheus
2.  **Alta Tasa Errores:** % HTTP 5xx >10% durante 2 min
3.  **Alta Latencia:** latencia promedio >2s durante 2 min
4.  **Contenedor Reiniciado:** métrica de reinicios aumenta

### Canales (Contact Points) – configurar mínimo 1:
- **Discord Webhook** (Baja) – nativo en Grafana
- **Telegram Bot** (@BotFather + chat_id) (Baja) – nativo
- Slack (Media) – Incoming Webhook
- Email (Media) – necesita SMTP / Mailhog

Documentar en README canal elegido + cómo se configuró.

## 9. Pruebas: Simulación de Caos

Flujo obligatorio:

1.  `docker-compose up --build`
2.  Verificar stack: Prometheus `http://localhost:9090/targets` todos UP, Grafana `http://localhost:3000` con métricas, Zipkin `http://localhost:9411` o Jaeger `16686`.
3.  Generar tráfico: `curl -X POST http://localhost/empleados -H "Content-Type..." -H "Authorization: Bearer " -d '{"id":"E010",...}'` + loop `for i in {1..10}; do curl ...`
4.  Verificar traza completa: gateway → empleados-service → departamentos-service → message broker → auth-service → notificaciones-service y perfiles-service.
5.  **Caos 1 - Apagar servicio:** `docker-compose stop departamentos-service`, esperar 2 min → verificar en Prometheus target DOWN, Grafana panel rojo, alerta llega a canal.
6.  **Caos 2 - Inducir errores/latencia:** `if random.random() < 0.5: time.sleep(5)` y rebuild.
7.  Documentar en README: captura dashboard con métricas reales, captura traza completa en Zipkin/Jaeger, captura alerta recibida (Discord/Telegram), y responder: **¿Qué servicio tardó más y cómo lo identificaron?**

## 10. Entregables

- Código versionado GitHub
- `observability/prometheus/prometheus.yml`
- `observability/grafana/provisioning/` (datasources + dashboards JSON)
- `docker-compose.yml` actualizado con observabilidad
- README con:
  - Diagrama arquitectura observabilidad (paso 1)
  - Investigación Pull vs Push, OTel, W3C
  - Librerías usadas por servicio para métricas y trazas
  - Elección Zipkin vs Jaeger
  - Canal de alertas + config
  - Capturas pruebas caos
  - Respuesta pregunta análisis

## 11. Consideraciones

- Todos los servicios instrumentados, no solo nuevos.
- URLs observabilidad en compose: nombres de red Docker, no localhost.
- Datasources/dashboards Grafana versionados en Git, disponibles auto con `up`, sin config manual.
- Simulación caos obligatoria con evidencia en README.

## 12. Criterios de Evaluación (5 puntos)

| # | Elemento | Valor |
| :--- | :--- | :--- |
| 1 | Infraestructura observabilidad (Prometheus, Grafana, Loki/Promtail, Zipkin/Jaeger en compose, red, prometheus.yml con 7 targets) | 1.0 |
| 2 | Instrumentación microservicios (métricas + /health + OTel con service.name, propagación W3C, traza onboarding completa extremo a extremo en UI) | 1.0 |
| 3 | Logs centralizados (JSON estructurado, Loki+Promtail, Grafana Loki datasource, ≥1 consulta LogQL) | 1.0 |
| 4 | Dashboard Grafana (≥4 paneles, 2 rows, provisionado auto en JSON) | 0.5 |
| 5 | Alertas proactivas (≥2 reglas, canal externo funcional, captura) | 0.5 |
| 6 | Pruebas caos y documentación (capturas dashboard, traza, alerta, diagrama, análisis) | 1.0 |

> README y versionamiento Git son transversales y afectan todos los puntos.

## 13. Diagrama Esperado

API Gateway → servicios → Prometheus scrapes, Grafana lee Prometheus y Loki, microservicios push trazas a Zipkin/Jaeger, Promtail lee docker.sock → Loki.
