# Guía de ejecución y entrega al equipo — Reto 9

Fecha de actualización: 18 de noviembre de 2026.

Esta guía explica qué cambió, qué necesita preparar cada integrante y cómo
iniciar el proyecto sin exponer secretos. Los comandos se ejecutan desde la raíz
del repositorio, donde están `docker-compose.yml` y `.env.example`.

## 1. Resumen ejecutivo

El proyecto ya no entrega contraseñas directamente a los contenedores mediante
el archivo `.env`. Las credenciales de ejecución se guardan en archivos locales
ignorados por Git y Docker Compose los monta en `/run/secrets/` únicamente en los
servicios que los necesitan.

También se implementaron:

- lectores de secretos en los siete microservicios;
- fallo explícito si falta un secreto, está vacío o conserva un marcador;
- rotación atómica del secreto JWT con respaldo para rollback;
- integración opcional de HashiCorp Vault para `auth-service`;
- monitoreo continuo de las siete bases PostgreSQL con Prometheus;
- alertas por base caída y por pérdida del exporter;
- dashboard de disponibilidad PostgreSQL en Grafana.

Los secretos reales no se comparten por Git, correo o chat. Cada integrante debe
generar sus propios archivos locales con el script incluido.

## 2. Prerrequisitos

Para el arranque con Compose se necesita:

- Git;
- Docker Engine con Docker Compose v2, o Docker Desktop;
- Bash, `openssl` y `awk`, requeridos por `scripts/setup-secrets.sh`;
- puertos locales libres: `8088`, `15672`, `9091`, `3000`, `3100` y `9411` para
  aplicación y observabilidad; el stack completo usa además `9000`, `9090` y
  `50000`;
- almacenamiento suficiente para las imágenes y volúmenes seleccionados.

El stack completo es pesado porque incluye siete microservicios, siete bases,
RabbitMQ, Jenkins, SonarQube y observabilidad. Si el equipo no tiene capacidad de
almacenamiento, debe usar otra máquina, una VM o un entorno remoto. No es necesario
levantar Jenkins y SonarQube para probar la aplicación.

## 3. Preparación inicial por integrante

### 3.1 Obtener los cambios

Después de actualizar la rama de trabajo, entrar en la raíz del proyecto y
confirmar que existen `.env.example`, `docker-compose.yml` y `scripts/`.

No copiar el `.env` ni la carpeta `secrets/` de otro integrante.

### 3.2 Generar configuración y secretos locales

En Linux, macOS o Git Bash:

```bash
chmod +x scripts/setup-secrets.sh scripts/rotate-secret.sh
./scripts/setup-secrets.sh
```

En Windows PowerShell, desde la raíz del repositorio:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\setup-secrets.ps1
```

En el Símbolo del sistema de Windows (`cmd.exe`):

```bat
scripts\setup-secrets.cmd
```

El archivo `.cmd` localiza automáticamente `powershell.exe` o `pwsh.exe`, ejecuta
la misma implementación segura y devuelve un código distinto de cero si ocurre
algún error.

El cambio de política aplica únicamente a la ventana actual y no necesita
permisos de administrador. El script es compatible con Windows PowerShell 5.1 y
PowerShell 7. No requiere instalar OpenSSL porque usa el generador criptográfico
de .NET.

El script:

- crea `.env` desde `.env.example` si todavía no existe;
- crea la carpeta local `secrets/`;
- genera valores aleatorios sin mostrarlos en pantalla;
- aplica permisos `600` en Unix o ACL restringida al usuario actual en Windows;
- conserva un secreto existente válido y no lo rota silenciosamente;
- reemplaza las antiguas contraseñas del `.env` por marcadores
  `GESTIONADO_EN_SECRETS_*`.

Se generan los siguientes archivos locales:

```text
secrets/
├── auth_admin_password.txt
├── broker_password.txt
├── db_auth_password.txt
├── db_departamento_password.txt
├── db_empleado_password.txt
├── db_notificaciones_password.txt
├── db_perfiles_password.txt
├── db_vacaciones_password.txt
├── discord_webhook_url.txt
├── grafana_admin_password.txt
├── jenkins_admin_password.txt
├── jwt_secret.txt
├── rabbitmq_erlang_cookie.txt
├── sonar_db_password.txt
├── sonar_token.txt
└── vault_root_token.txt
```

`.env` y `secrets/` están ignorados por Git. Antes de hacer commit se debe
confirmar que no aparecen en `git status`.

### 3.3 Valores que requieren configuración manual

- `secrets/sonar_token.txt` contiene inicialmente un marcador no funcional. Solo
  es necesario reemplazarlo si se usarán Jenkins y los análisis de SonarQube.
- `secrets/discord_webhook_url.txt` contiene un endpoint ficticio. Reemplazarlo
  únicamente si el equipo quiere recibir las alertas en Discord.
- Revisar los puertos y URLs no sensibles de `.env` si existe algún conflicto
  local. No volver a escribir allí contraseñas reales.
- Para las pruebas E2E del ciclo de vacaciones, cambiar
  `VACACIONES_MANUAL_ENABLED=true`; para operación normal debe permanecer `false`.

## 4. Formas de iniciar el proyecto

### Opción A — Aplicación mínima recomendada

Esta opción inicia el Gateway y, por dependencias, los seis microservicios de
negocio, las seis bases y RabbitMQ. No inicia Jenkins, SonarQube ni observabilidad.

```bash
docker compose up -d --build gateway-service
```

Es la opción con menor consumo para validar las funciones del sistema. El punto
de entrada queda en `http://localhost:8088` y RabbitMQ Management en
`http://localhost:15672`.

### Opción B — Aplicación y observabilidad completa

Además de la aplicación, inicia Prometheus, Grafana, Loki, Promtail, Zipkin y los
exporters PostgreSQL. Se incluye `sonar-db` sin iniciar SonarQube para que los siete
targets de base de datos puedan quedar disponibles.

```bash
docker compose up -d --build \
  gateway-service \
  prometheus grafana loki promtail zipkin \
  empleado-db-exporter departamento-db-exporter \
  notificaciones-db-exporter perfiles-db-exporter \
  vacaciones-db-exporter auth-db-exporter \
  sonar-db sonar-db-exporter
```

Direcciones principales:

| Componente | URL | Uso |
|---|---|---|
| API Gateway | `http://localhost:8088` | Entrada a la aplicación |
| Swagger agregado | `http://localhost:8088/swagger-ui.html` | Documentación de APIs |
| RabbitMQ | `http://localhost:15672` | Colas y mensajes |
| Prometheus | `http://localhost:9091` | Consultas, targets y alertas |
| Grafana | `http://localhost:3000` | Dashboards y alertas |
| Loki | `http://localhost:3100` | Almacenamiento de logs |
| Zipkin | `http://localhost:9411` | Trazas distribuidas |

La contraseña de Grafana está en `secrets/grafana_admin_password.txt` y la de
RabbitMQ en `secrets/broker_password.txt`. No se deben imprimir ni incluir en
capturas o documentación.

### Opción C — Stack completo con CI

```bash
docker compose up -d --build
```

Este comando también inicia Jenkins y SonarQube. Debe usarse únicamente en una
máquina con espacio suficiente. El perfil `vault` y el perfil `build-tools` no se
activan con este comando.

Antes de usar los pipelines de Jenkins se debe completar la configuración de
SonarQube y reemplazar `secrets/sonar_token.txt` por un token válido.

### Opción D — Aplicación con Vault

Vault es opcional y está configurado en modo `dev` solo para el laboratorio. Sus
datos se pierden al recrear el contenedor y esta configuración no es apta para
producción.

```bash
VAULT_ADDR=http://vault:8200 \
docker compose --profile vault up -d --build \
  vault vault-bootstrap gateway-service
```

`vault-bootstrap` carga los cuatro secretos de Auth en KV v2, configura AppRole y
entrega a `auth-service` credenciales limitadas mediante el volumen
`vault-approle`. `auth-service` nunca recibe el token raíz.

## 5. Verificación después del arranque

### 5.1 Estado general

```bash
docker compose ps
docker compose logs --tail=100 gateway-service auth-service
curl -fsS http://localhost:8088/health
```

Los servicios deben terminar en estado `healthy`. Si alguno reinicia o no alcanza
salud, consultar únicamente sus logs:

```bash
docker compose logs --tail=200 NOMBRE_DEL_SERVICIO
```

Los mensajes de fallo de secretos indican el nombre del archivo ausente, pero
nunca deben mostrar su contenido.

### 5.2 Bases de datos en Prometheus

En `http://localhost:9091/targets`, el job `postgresql-databases` debe mostrar
siete targets `UP`. También se puede consultar la regla consolidada:

```bash
curl -fsS \
  'http://localhost:9091/api/v1/query?query=database%3Apostgresql_available'
```

Cada serie debe valer `1`. La identificación se realiza mediante las etiquetas
`database`, `db_service` y `owner_service`.

Las señales implementadas son:

- `pg_up = 0`: el exporter responde, pero PostgreSQL no acepta la conexión;
- `up = 0`: Prometheus no puede consultar el exporter;
- `database:postgresql_available`: combina ambas señales para el dashboard;
- `PostgreSQLDatabaseDown`: alerta por base caída durante 30 segundos;
- `PostgreSQLExporterDown`: alerta por pérdida del monitoreo durante 30 segundos.

En Grafana se debe abrir la carpeta **Microservicios** y el dashboard
**PostgreSQL - Disponibilidad**. Se actualiza cada 10 segundos y muestra estado,
historial y conexiones por base.

## 6. Cambios técnicos entregados

### 6.1 Configuración y Docker Secrets

- `.env.example` conserva solo marcadores documentales para secretos.
- `scripts/setup-secrets.sh` genera la configuración local segura.
- `docker-compose.yml` declara los secretos como archivos y aplica mínimo acceso
  por servicio.
- PostgreSQL y Grafana usan sus mecanismos `*_FILE`.
- RabbitMQ, SonarQube y Jenkins leen el archivo en su proceso de entrada cuando la
  imagen no ofrece un mecanismo equivalente.

### 6.2 Lectura segura en microservicios

Se incorporaron lectores en:

- `.NET`: `empleado-service/empleadoService/infrastructure/configuration/SecretReader.cs`;
- Python: `departamento-service/app/config/secrets.py`;
- Node.js: `notificaciones-service/src/config/secrets.js`;
- Go: `perfiles-service/internal/config/config.go`;
- Java: `SecretLoader` en Vacaciones, Auth y Gateway;
- Vault: `VaultSecretLoader` en Auth.

El orden es archivo montado, luego variable de entorno solo para desarrollo local.
No existe contraseña por defecto. El proceso falla explícitamente cuando el valor
falta, está vacío, no puede leerse o conserva un marcador inseguro.

### 6.3 Rotación

La rotación JWT se ejecuta con:

```bash
./scripts/rotate-secret.sh jwt
docker compose up -d --force-recreate auth-service gateway-service
```

El script reemplaza el archivo de forma atómica, conserva un backup local con
permisos restringidos y no imprime los valores. La rotación invalida todos los JWT
emitidos anteriormente.

Para PostgreSQL o RabbitMQ no basta con reemplazar el archivo: primero debe
cambiarse la credencial en el servidor, después actualizar el secret y finalmente
recrear sus consumidores. En bases con volumen existente,
`POSTGRES_PASSWORD_FILE` solo se usa durante la inicialización y no modifica por sí
mismo la contraseña persistida.

### 6.4 Vault

- `vault/server-dev.sh`: inicia Vault de laboratorio leyendo el token raíz desde
  un archivo.
- `vault/bootstrap.sh`: habilita KV v2 y AppRole sin imprimir credenciales.
- `vault/policies/auth-service.hcl`: concede a Auth solo lectura sobre sus rutas.
- `auth-service` usa AppRole y tokens temporales; mantiene Docker Secrets como
  mecanismo predeterminado cuando `VAULT_ADDR` está vacío.

### 6.5 Monitoreo PostgreSQL

- una instancia `postgres_exporter:v0.21.0` por base;
- contraseñas montadas con `--datasource.pass-file`;
- scraping cada 10 segundos en `observability/prometheus/prometheus.yml`;
- reglas en `observability/prometheus/rules/database-alerts.yml`;
- dashboard en
  `observability/grafana/provisioning/dashboards/postgresql.json`;
- alerta de Grafana conectada al canal aprovisionado;
- pruebas estáticas en `observability/tests/observability_config_test.rb`.

## 7. Pruebas que pueden ejecutarse sin levantar Docker

Estas pruebas no consumen imágenes ni modifican los secretos reales:

```bash
./scripts/tests/rotate-secret-test.sh
./scripts/tests/vault-server-test.sh
./scripts/tests/vault-bootstrap-test.sh
ruby observability/tests/observability_config_test.rb
```

La última validación del módulo de observabilidad produjo 6 pruebas, 89
aserciones, 0 fallos y 0 errores.

## 8. Actualización de un entorno existente

Quien ya tenga volúmenes PostgreSQL o RabbitMQ debe conservar las credenciales que
esos volúmenes conocen. Ejecutar el generador con nuevos valores y reutilizar un
volumen antiguo puede producir `password authentication failed`.

Opciones seguras:

1. colocar en los archivos de `secrets/` las credenciales actuales antes de
   arrancar; o
2. realizar la rotación coordinada descrita en el README; o
3. eliminar únicamente los volúmenes concretos después de confirmar que los datos
   locales son desechables y que existe respaldo.

No usar `docker compose down -v` como operación rutinaria: elimina todos los
volúmenes del proyecto, incluidas bases de datos y datos de Jenkins, SonarQube,
Prometheus, Grafana y Loki.

Para detener contenedores conservando los datos:

```bash
docker compose down
```

## 9. Lista de verificación para el equipo

- [ ] Se actualizó la rama sin copiar secretos de otro integrante.
- [ ] Se ejecutó `scripts/setup-secrets.sh` una sola vez por entorno.
- [ ] `.env` y `secrets/` no aparecen en `git status`.
- [ ] Se eligió el modo de arranque adecuado para la capacidad del equipo.
- [ ] Todos los microservicios requeridos están `healthy`.
- [ ] El Gateway responde en `/health`.
- [ ] Los siete targets PostgreSQL aparecen `UP` si se inició la observabilidad completa.
- [ ] Grafana muestra el dashboard PostgreSQL.
- [ ] Sonar y Discord solo se configuraron si realmente se van a utilizar.
- [ ] No se incluyeron secretos en commits, logs compartidos o capturas.

## 10. Limitaciones conocidas

- Vault está en modo `dev`, sin TLS ni persistencia; es demostrativo.
- La ejecución real del stack no se validó en el equipo donde se implementaron
  estos cambios por la restricción de almacenamiento indicada. Las configuraciones
  YAML/JSON y las pruebas sin Docker sí fueron validadas.
- La alerta de Discord no funcionará mientras el archivo conserve el webhook
  ficticio.
- Jenkins no completará análisis de SonarQube mientras `sonar_token.txt` conserve
  el marcador inicial.

