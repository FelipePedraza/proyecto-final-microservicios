# Reto 8 – Integrante 2 | Puntos 3 y 4.1 [PRECISO]

> **Equipo:** 4 desarrolladores | **Mi rol:** Integrante 2
> **Puntos asignados:** 3. Config Prometheus + 4.1 Exposición Métricas
> **Base:** README principal ya tiene Puntos 1 y 2 hechos por Integrante 1

---

## 0. Estado Actual por Integrante 1 (NO TOCAR BAJO NINGUNA CIRCUNSTANCIA)

Según tu README y las 3 capturas que enviaste:

- **Stack levantado y verificado:**
  - Prometheus interno `:9090` -> host `${PROMETHEUS_HOST_PORT}` (por defecto `9091` porque Jenkins ocupa 9090)
  - Grafana `:3000` -> host `${GRAFANA_HOST_PORT}` = 3000
  - Zipkin `:9411` -> host `${ZIPKIN_HOST_PORT}` = 9411 (elegido sobre Jaeger por simplicidad, exportador Zipkin OTel)
  - Loki `:3100` -> host `${LOKI_HOST_PORT}` = 3100
  - Promtail con acceso a `/var/run/docker.sock` y `LOKI_PUSH_URL=http://loki:3100/loki/api/v1/push`
  - `grafana-data`, `prometheus-data`, `loki-data` con volúmenes persistentes
  - Datasource aprovisionado: `observability/grafana/provisioning/datasources/datasources.yml` con `PROMETHEUS_DATASOURCE_URL=http://prometheus:9090`
  - Todos en red `microservicios-network`
  - `.env` centralizado: `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`, `PROMETHEUS_HOST_PORT`, etc. -> **Actualizar tu .env local desde .env.example**

**Capturas que prueban infra OK:**
1. `image_968dea.png` -> Zipkin UI en `127.0.0.1:9411/zipkin/` en Search Traces
2. `image_5ac00f.png` -> Grafana en `127.0.0.1:3000` vacío listo para dashboards
3. `image_daa9c1.png` -> Docker Desktop con `promtail`, `empleado-service-1`, `notificaciones-service-1`, `sonarqube`, `jenkins` corriendo

### 🔴 REGLA DE ORO DEL REPO

> **Integrantes 3 y 4: PROHIBIDO tocar:**
> - `docker-compose.yml` (sección observabilidad)
> - `observability/` (prometheus/, grafana/provisioning/, promtail/, loki/)
> - `.env.example` sin avisar
> - `microservicios-network`
>
> **Integrante 2 tampoco toca 4.2, 4.3, 4.4, 5, 6.** Esos son de ustedes.
> Si mi trabajo necesita que agreguen algo, lo aviso en el grupo y lo documento aquí.

---

## 1. División Final Acordada (4 devs)

| Integrante | Puntos PDF | Tarea | Estado |
| :--- | :--- | :--- | :--- |
| 1 | 1 + 2 | Investigación 3 pilares, Pull/Push, OTel, W3C + Infra Docker Compose + Diagrama Mermaid | ✅ HECHO - en README principal líneas 685-850 |
| **2 (YO)** | **3 + 4.1** | **prometheus.yml con 7 jobs + exponer /metrics en 7 servicios** | **🔄 ESTE ARCHIVO** |
| 3 | 4.2 + 4.3 | Trazabilidad OpenTelemetry + /health checks reales | ⏳ ESPERAR MI PR |
| 4 | 4.4 + 5 + 6 | Logs JSON Loki + Dashboard Grafana + Alertas Discord/Telegram | ⏳ ESPERAR MI PR |

---

## 2. PUNTO 3: Configuración Prometheus - MI TRABAJO PRECISO

### 2.1 Contexto de puertos REALES del README principal

| Servicio | Carpeta | Puerto interno REAL | Nombre Docker | Metrics path que debo habilitar |
| :--- | :--- | :--- | :--- | :--- |
| `gateway-service` | `gateway-service/` | `8088` | `gateway-service` | `/actuator/prometheus` (Spring Boot) |
| `empleado-service` | `empleado-service/` | `8080` | `empleado-service` | `/metrics` (.NET) |
| `departamento-service` | `departamento-service/` | `8081` | `departamento-service` | `/metrics` (FastAPI) |
| `notificaciones-service` | `notificaciones-service/` | `8082` | `notificaciones-service` | `/metrics` (Express) |
| `perfiles-service` | `perfiles-service/` | `8083` | `perfiles-service` | `/metrics` (Go chi) |
| `vacaciones-service` | `vacaciones-service/` | `8085` | `vacaciones-service` | `/actuator/prometheus` (Spring Boot) |
| `auth-service` | `auth-service/` | `8089` | `auth-service` | `/actuator/prometheus` (Spring Boot) |

> **IMPORTANTE:** En `docker-compose.yml` solo `gateway-service`, `rabbitmq` y `jenkins/sonarqube` publican puertos al host. Los demás usan `expose:`. Por eso en `prometheus.yml` uso **nombre:PUERTO_INTERNO**, no localhost ni puerto host.

### 2.2 Archivo que VOY A EDITAR: `observability/prometheus/prometheus.yml`

**Este archivo ya existe pero vacío/incompleto. Yo lo completo.**

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # JOB 1 - OBLIGATORIO PRIMERO SEGÚN PDF: API Gateway (punto entrada cliente)
  - job_name: 'gateway-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['gateway-service:8088']
        labels:
          service: 'gateway-service'
          lenguaje: 'java-spring'

  # JOB 2 - Empleado .NET
  - job_name: 'empleado-service'
    metrics_path: '/metrics'
    static_configs:
      - targets: ['empleado-service:8080']
        labels:
          service: 'empleado-service'
          lenguaje: 'dotnet'

  # JOB 3 - Departamento Python
  - job_name: 'departamento-service'
    metrics_path: '/metrics'
    static_configs:
      - targets: ['departamento-service:8081']
        labels:
          service: 'departamento-service'
          lenguaje: 'python-fastapi'

  # JOB 4 - Notificaciones Node
  - job_name: 'notificaciones-service'
    metrics_path: '/metrics'
    static_configs:
      - targets: ['notificaciones-service:8082']
        labels:
          service: 'notificaciones-service'
          lenguaje: 'nodejs-express'

  # JOB 5 - Perfiles Go
  - job_name: 'perfiles-service'
    metrics_path: '/metrics'
    static_configs:
      - targets: ['perfiles-service:8083']
        labels:
          service: 'perfiles-service'
          lenguaje: 'go'

  # JOB 6 - Vacaciones Java
  - job_name: 'vacaciones-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['vacaciones-service:8085']
        labels:
          service: 'vacaciones-service'
          lenguaje: 'java-spring'

  # JOB 7 - Auth Java
  - job_name: 'auth-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['auth-service:8089']
        labels:
          service: 'auth-service'
          lenguaje: 'java-spring'
```

### 2.3 Validación de mi trabajo Punto 3
```bash
docker compose up -d --build
curl http://localhost:9091/-/healthy  # Prometheus host es 9091, no 9090
# Abrir http://localhost:9091/targets -> 7 targets UP
# Abrir http://localhost:9091/graph -> query: up  debe dar 1 para cada job
```

Si algún target DOWN, es porque el servicio aún no expone /metrics (mi Punto 4.1).

---

## 3. PUNTO 4.1: Exposición de Métricas - MI TRABAJO PRECISO POR SERVICIO

### Objetivo del PDF:
> "Cada microservicio debe exponer un endpoint que retorne métricas en texto plano en formato Prometheus. Métricas mínimas: CPU/memoria, número peticiones HTTP por código, latencia."

### 3.1 Implementación por tecnología REAL de nuestro repo

#### A) gateway-service, vacaciones-service, auth-service (Java 21 Spring Boot)
**Ya tienen Actuator, solo hay que exponer prometheus**

En `src/main/resources/application.yml` o `application.properties` de cada uno:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,info
  endpoint:
    health:
      show-details: always
    prometheus:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
```

Dependencia Maven (verificar en pom.xml, si falta agregar):
```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Endpoint resultante: `http://gateway-service:8088/actuator/prometheus` -> texto plano Prometheus.

#### B) empleado-service (.NET 10 ASP.NET Core)
**Usar prometheus-net**

En `empleado-service.csproj`:
```xml
<PackageReference Include="prometheus-net.AspNetCore" Version="8.2.1" />
```

En `Program.cs`:
```csharp
using Prometheus;
app.UseHttpMetrics(); // mide latencia y conteo automático
app.MapMetrics("/metrics"); // expone /metrics sin auth
```

Métricas que genera: `http_request_duration_seconds`, `dotnet_total_memory_bytes`, etc.

#### C) departamento-service (Python 3.11 FastAPI 0.116)
**Usar prometheus-fastapi-instrumentator**

En `requirements.txt`:
```
prometheus-fastapi-instrumentator==6.1.0
```

En `main.py` o `app.py`:
```python
from prometheus_fastapi_instrumentator import Instrumentator

app = FastAPI(...)
Instrumentator().instrument(app).expose(app, endpoint="/metrics")
```

#### D) notificaciones-service (Node.js Express)
**Usar prom-client**

```bash
npm install prom-client
```

```javascript
const client = require('prom-client');
const collectDefaultMetrics = client.collectDefaultMetrics;
collectDefaultMetrics({ prefix: 'notificaciones_' });

const httpRequestDuration = new client.Histogram({
  name: 'http_request_duration_seconds',
  help: 'Duration of HTTP requests',
  labelNames: ['method', 'route', 'status_code'],
  buckets: [0.1, 0.3, 0.5, 0.7, 1, 3, 5, 7, 10]
});

app.get('/metrics', async (req, res) => {
  res.set('Content-Type', client.register.contentType);
  res.end(await client.register.metrics());
});

// middleware para medir
app.use((req, res, next) => {
  const end = httpRequestDuration.startTimer();
  res.on('finish', () => end({ method: req.method, route: req.route?.path || req.path, status_code: res.statusCode }));
  next();
});
```

#### E) perfiles-service (Go 1.25 chi router)
**Usar client_golang**

```go
import (
  "github.com/prometheus/client_golang/prometheus/promhttp"
  "net/http"
)

r.Handle("/metrics", promhttp.Handler())
// Instrumentar con middleware de chi para latencia
```

### 3.2 Qué NO voy a hacer en 4.1 (para no pisar a otros)
- NO configuro OpenTelemetry / Zipkin (eso es 4.2 de Integrante 3)
- NO hago /health real con checks a DB/broker (eso es 4.3 de Integrante 3)
- NO formateo logs a JSON ni agrego traceId (eso es 4.4 de Integrante 4)
- Solo métricas.

### 3.3 Cómo mi trabajo afecta a los otros
- Cuando mi `/metrics` esté arriba, Integrante 3 podrá ver en Prometheus si su instrumentación OTel rompe algo.
- Integrante 4 usará mis métricas `http_requests_total` para sus paneles de Grafana.
- Si necesitan label custom, me avisan y yo lo agrego.

---

## 4. Coordinación y Entregables Míos

### Checklist PR Integrante 2
- [ ] `observability/prometheus/prometheus.yml` con 7 jobs, gateway primero, usando `nombre:PUERTO_INTERNO` real del README
- [ ] `.env.example` actualizado si agrego `METRICS_PATH_*` (pero NO tocar las variables de Integrante 1)
- [ ] Cada servicio responde `curl` interno: `docker compose exec -T prometheus wget -qO- http://empleado-service:8080/metrics | head -20`
- [ ] Capturas para README principal:
  - `http://localhost:9091/targets` con 7 UP (recordar host es 9091)
  - `http://localhost:9091/graph?g0.expr=up` 
  - `http://localhost:3000` con datasource Prometheus OK
- [ ] Tabla final documentada en README principal (sección nueva 3. y 4.1)

### Mensaje para Integrantes 3 y 4
> "Chicos, no toquen nada de métricas ni prometheus.yml hasta que mi PR esté mergeado. Si ven que su servicio no tiene /metrics, es porque estoy en eso. Cuando termine aviso en Discord/WhatsApp. Si necesitan que mi endpoint sea sin auth, ya está: /metrics y /actuator/prometheus van públicos sin JWT, porque Prometheus no puede autenticarse."

### Si algo de mi trabajo rompe lo de ustedes
Si al agregar `prometheus-net` o `micrometer-registry-prometheus` cambia algún puerto o requiere variable nueva, **lo documento en este README y aviso inmediatamente en el grupo**.

---

## 5. Referencias al PDF original

- Pág 4-6 del reto08.pdf: Ejemplo `prometheus.yml` + nota de 7 objetivos (no 2) + gateway primero + `metrics_path` según lenguaje
- Pág 6: Tabla librerías referencia y métricas mínimas (CPU, peticiones por código, latencia)
- Pág 2: Infra debe estar en misma red Docker (ya hecho por Integrante 1)

Este archivo es mi bitácora. Al final se mergea resumido al README principal en la sección "3. Configuración de Prometheus" y "4.1 Exposición de Métricas".
