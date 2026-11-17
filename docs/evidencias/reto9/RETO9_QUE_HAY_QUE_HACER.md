# Reto 9: Externalización de configuración y gestión de secretos

## 1. Objetivo

Eliminar secretos hardcodeados y secretos versionados del ecosistema. Al finalizar,
la configuración sensible debe provenir de `.env` local o de archivos montados por
Docker Compose en `/run/secrets/`, y el repositorio debe seguir levantándose con
`docker compose up --build`.

El reto se divide en auditoría, dos niveles obligatorios, rotación y, opcionalmente,
HashiCorp Vault.

## 2. Requisitos generales

- No hay contraseñas, tokens, API keys, cookies, JWT secrets ni credenciales de
  herramientas hardcodeados en código o `docker-compose.yml`.
- Solo existe `.env.example` con marcadores; el `.env` real no se versiona.
- `secrets/` y `secrets.example/` tienen roles diferentes: `secrets/` contiene
  valores locales ignorados por Git y `secrets.example/` contiene únicamente
  plantillas versionables.
- Los microservicios pueden leer variables de entorno o archivos
  `/run/secrets/<nombre>`, con fallback seguro a entorno cuando corresponda.
- Los secretos vacíos o con valores de ejemplo deben provocar un fallo explícito.
- El README raíz documenta investigación conceptual, auditoría, instalación,
  configuración y rotación.

## 3. Trabajo completo por etapas

### 3.1 Investigación previa

Documentar en el README:

1. Twelve-Factor App, Factor III: configuración, código y publicación abierta.
2. Variables de entorno frente a archivos en `/run/secrets/`, exposición por
   `docker inspect` y procesos.
3. Cómo funciona `.env` en Docker Compose y por qué `.env.example` sí se
   versiona mientras `.env` no.
4. Qué hace la sección `secrets:` con `file:` y qué ruta recibe el contenedor.
5. Rotación de secretos y por qué debe hacerse aun sin una fuga confirmada.

### 3.2 Auditoría manual

Crear una tabla completa del ecosistema con servicio, tipo de secreto,
ubicación actual y mecanismo objetivo. Deben cubrirse:

| Origen | Alcance mínimo |
|---|---|
| Bases de datos | empleado, departamento, auth, perfiles, vacaciones y notificaciones |
| SonarQube | contraseña de PostgreSQL y/o credenciales de integración |
| Broker | usuario, contraseña y cookie Erlang de RabbitMQ |
| JWT | secreto compartido por auth y gateway |
| Jenkins | usuario y contraseña administrativos |
| Grafana | usuario y contraseña administrativos |
| E2E | contraseña administrativa y secreto JWT temporal |
| Alertas/terceros | webhook de Discord si contiene datos sensibles |
| SMTP | credenciales de correo, si el envío real las usa |

Además se deben revisar `docker-compose.yml`, archivos de configuración, archivos
`.env` existentes y cadenas hardcodeadas en el código fuente.

### 3.3 Auditoría automatizada

Ejecutar `detect-secrets` sobre el estado actual o `truffleHog` sobre el historial.
Guardar evidencia o captura y describir en el README qué hallazgos aparecieron y
cómo se remediaron.

Si un secreto real estuvo en Git, se considera comprometido permanentemente:
hay que generar todos los valores nuevos necesarios, documentar la remediación y
revisar además ramas y historial.

### 3.4 Nivel 1: `.env` y `.gitignore`

Entregables mínimos:

- `.env.example` completo, con marcadores, sin valores reales.
- `.env` real local e ignorado por Git.
- `.gitignore` con `.env`, `*.env`, `.env.local`, `.env.*.local` y `secrets/`.
- `docker-compose.yml` sin valores reales y con referencias obligatorias
  (`${VARIABLE:?...}` donde sea apropiado).
- `scripts/setup-secrets.sh` para generar el `.env` o los archivos reales
  locales con `openssl`.
- Permisos restrictivos para archivos sensibles.

Validar que un clon nuevo pueda copiar la plantilla, generar los secretos e iniciar
todo el ecosistema con un único comando.

### 3.5 Nivel 2: Docker Compose Secrets

Entregables mínimos:

- Sección global `secrets:` en `docker-compose.yml`, con una entrada `file:` por
  secreto sensible.
- Solo los servicios que necesitan un secreto deben recibirlo; aplicar mínimo
  privilegio.
- Cada microservicio consulta primero `/run/secrets/<nombre>` y luego hace
  fallback controlado a variable de entorno.
- Los usuarios y contraseñas no sensibles pueden seguir como variables de entorno
  si no son secretos.
- Las bases de datos reciben simultáneamente el secreto correcto en su
  inicialización y en el cliente correspondiente.
- Mensaje claro si falta el secreto, sin fallbacks silenciosos ni valores
  de ejemplo aceptados.

Pruebas obligatorias:

1. `docker compose up --build` levanta el ecosistema completo.
2. `docker inspect <contenedor>` no muestra secretos ni contraseñas migradas.
3. `secrets/` no aparece en `git status` ni en el historial.
4. El servicio falla de forma descriptiva si falta su secreto.
5. Al retirar temporalmente un secreto requerido, el arranque falla controladamente
   y vuelve a funcionar al restaurarlo.

### 3.6 Rotación

Documentar el procedimiento de rotación y el impacto de cada categoría:

- JWT: invalida tokens activos y obliga a iniciar sesión nuevamente.
- Contraseña de BD: actualizar primero el secreto y la configuración del contenedor
  con la BD de forma compatible, reiniciar solo los afectados y verificar.
- Contraseña de RabbitMQ: actualizar broker y todos los productores/consumidores.
- Jenkins, Grafana y Sonar: actualizar usuario/contraseña/token y validar integraciones.
- Erlang cookie de RabbitMQ: reiniciar el clúster o nodos según la topología.

Debe incluirse la evidencia de al menos una rotación real del JWT y sus efectos
observados.

### 3.7 Nivel 3: Vault (opcional, hasta +1 punto)

Solo después de completar los niveles obligatorios:

- Desplegar HashiCorp Vault en Docker Compose.
- Inicializar secretos sin usar el token raíz fuera del entorno de aprendizaje.
- Conectar al menos un microservicio a Vault al arrancar.
- Documentar AppRole y por qué es preferible a un token raíz en producción.

Vault no reemplaza los niveles 1 y 2.

## 4. Evidencias y entregables

- Código y configuración versionados.
- `.env.example` completo.
- `.gitignore` actualizado.
- `secrets.example/` con marcadores.
- `scripts/setup-secrets.sh`.
- `docker-compose.yml` actualizado.
- README raíz con investigación, tabla de auditoría, escaneo, configuración inicial,
  rotación y, si aplica, integración con Vault.
- Evidencia de arranque, `docker inspect`, limpieza de Git y rotación.

## 5. Criterios de evaluación

| Elemento | Valor |
|---|---:|
| Auditoría y diagnóstico | 1.0 |
| Nivel 1: `.env` y `.gitignore` | 1.0 |
| Nivel 2: Docker Compose Secrets | 2.0 |
| Rotación de secretos | 1.0 |
| Vault (bonus) | +1.0 |

## 6. Estado observado al iniciar

- Ya existe `.env.example`, pero contiene claves secretas con valores vacíos y
  puede ampliarse con estructura completa de secretos.
- El `.gitignore` excluye `.env` y `secrets/`, pero no todas las variantes
  obligatorias (`*.env`, `.env.*.local`) aparecen todavía.
- `docker-compose.yml` usa referencias a `.env`, no valores reales visibles en el
  archivo actual, pero todavía no usa `secrets:`.
- Hay fallbacks hardcodeados en código de servicios como vacaciones, notificaciones
  y departamento; estos deben revisarse y sustituirse por política explícita.
- Hay credenciales expuestas para Jenkins, Sonar, Grafana, RabbitMQ y las seis
  bases de datos en el flujo actual de entorno, que deben migrarse según el mecanismo
  objetivo.
- Los commits recientes confirmaron el avance de Jenkins y los pipelines
  individuales. La migración de secretos debe coordinarse con esos servicios
  compartidos sin modificar fuera de los permisos de cada responsable.
