# Auditoría del Reto 9 — Punto 1

Fecha: 2026-11-17  
Responsable: Daniel Eduardo Jurado (Dan17i)  
Estado: auditoría manual y escaneo automático inicial completados; remediación
completada en la fase de diagnóstico y entregada para los niveles 1 y 2.

## 1. Resultado ejecutivo

El ecosistema tiene configuración sensible distribuida entre `.env.example`,
`docker-compose.yml`, configuración de microservicios y archivos de herramientas.
El arranque actual depende de variables de entorno y aún no usa Docker Compose
Secrets.

Se generó y clasificó un baseline con `detect-secrets` sin incluir valores
detectados en el reporte documental. El baseline contiene 15 hallazgos en 8
archivos: 3 verdaderos positivos y 12 falsos positivos de fixtures o documentación.

## 2. Investigación aplicada

- Twelve-Factor App Factor III: los secretos y demás configuración variable deben
  salir del código y de los artefactos versionados.
- Las variables de entorno pueden aparecer en `docker inspect`, procesos y archivos
  de diagnóstico. Los archivos `/run/secrets/` reducen esa exposición.
- Docker Compose carga `.env` automáticamente; `.env.example` debe ser seguro y
  versionado, mientras `.env` y `secrets/` quedan locales.
- La sección `secrets:` con `file:` monta archivos solo en los contenedores
  declarados, bajo la ruta `/run/secrets/<nombre>`.
- La rotación evita mantener indefinidamente un secreto estático y limita el daño
  acumulado si alguna vez fue expuesto.

## 3. Auditoría manual

| Origen | Secreto identificado | Ubicación actual | Objetivo |
|---|---|---|---|
| empleado-db / empleado-service | Contraseña PostgreSQL | `.env.example`, `docker-compose.yml` | `db_empleado_password` |
| departamento-db / departamento-service | Contraseña PostgreSQL | `.env.example`, Compose y fallback Python | `db_departamentos_password` |
| notificaciones-db / notificaciones-service | Contraseña PostgreSQL | `.env.example`, Compose y fallback Node.js | `db_notificaciones_password` |
| perfiles-db / perfiles-service | Contraseña PostgreSQL | `.env.example`, Compose y fallback Go | `db_perfiles_password` |
| vacaciones-db / vacaciones-service | Contraseña PostgreSQL | `.env.example`, Compose y fallback Spring | `db_vacaciones_password` |
| auth-db / auth-service | Contraseña PostgreSQL | `.env.example`, Compose | `db_auth_password` |
| RabbitMQ | Usuario, contraseña y Erlang cookie | `.env.example`, Compose y servicios | Secretos separados |
| auth/gateway | JWT Secret | `.env.example`, Compose y propiedades | `jwt_secret` |
| auth-service | Password de administrador inicial | `.env.example`, Compose | `auth_admin_password` |
| Grafana | Credencial administrativa | `.env.example`, Compose | `grafana_admin_password` |
| SonarQube | Contraseña de DB y token | `.env.example`, Compose y Jenkins | Secretos separados |
| Jenkins | Usuario, contraseña y credencial Sonar | `.env.example`, Compose y `casc.yaml` | Secretos separados |
| E2E | Password administrativo y JWT temporal | `.env.example` E2E y tests | Configuración local |
| Discord | Webhook | `.env.example`, Compose y Grafana | Secreto opcional |
| SMTP | Credenciales de correo | No implementado encontrado | Evaluar si se habilita |

Además se encontraron contraseñas conocidas usadas como fallback o fixture:

- `vacaciones-service`: fallback PostgreSQL y RabbitMQ.
- `departamento-service`: fallback PostgreSQL.
- `notificaciones-service`: fallback PostgreSQL y RabbitMQ.
- `perfiles-service`: usuario RabbitMQ por defecto y password configurable vacío.
- Pruebas de gateway y E2E: claves JWT y passwords exclusivamente de fixture.
- Pipeline de pruebas: contraseña PostgreSQL local para contenedor temporal.

## 4. Escaneo automatizado

Comando ejecutado:

```bash
python -m pip install --user detect-secrets
python -m detect_secrets scan \
  --exclude-files='(^|/)(\.git|target|bin|obj|node_modules|reports|TestResults|\.venv)(/|$)' \
  . > .secrets.baseline
```

Resultado resumido:

| Métrica | Resultado |
|---|---:|
| Hallazgos | 15 |
| Archivos afectados | 8 |
| `Secret Keyword` | 14 |
| `Base64 High Entropy String` | 1 |

Ubicaciones resumidas:

- Jenkins temporal de `departamento-service`.
- Documentación de Reto 5.
- Pruebas E2E.
- Pruebas unitarias de gateway.
- Configuración de `vacaciones-service`.

No se reprodujeron los valores detectados. El baseline fue clasificado y puede
consultarse con:

```bash
detect-secrets audit .secrets.baseline
detect-secrets audit --stats --json .secrets.baseline
```

Clasificación resumida:

| Clasificación | Cantidad | Ubicaciones |
|---|---:|---|
| Verdadero positivo | 3 | Pipeline temporal de PostgreSQL y dos fallbacks del servicio de vacaciones |
| Falso positivo | 12 | Ejemplos y contraseñas de documentación, fixtures E2E y claves JWT exclusivas de pruebas |

Interpretación práctica:

- Un falso positivo significa que el detector encontró un patrón parecido a
  credencial, pero el valor pertenece a documentación o pruebas controladas.
  Los 12 hallazgos no deben convertirse en secretos nuevos ni requieren rotación
  por sí solos.
- Un verdadero positivo significa que existe una configuración conocida o un
  fallback reconocible que debe eliminarse o reemplazarse por configuración externa.
- `.env.example` debe seguir con marcadores o variables vacías; no es el lugar
  para poner los secretos finales.
- `.env` real debe permanecer local e ignorado por Git.
- `docker-compose.yml` debe referenciar variables o montar archivos de
  `secrets/`, sin fijar credenciales reales.
- Los archivos `application.yml` deben retirar valores por defecto sensibles y
  exigir la configuración externa.
- El `Jenkinsfile` que usa PostgreSQL temporal necesita una credencial de prueba
  controlada, pero no una credencial real del entorno.

## 5. Historial Git

Se revisaron rutas sensibles en todos los objetos de Git:

- No se encontraron archivos `.env` reales en el historial.
- No se encontró `secrets/*.txt` en el historial.
- Persisten únicamente archivos `.env.example` históricos y una plantilla E2E.

Como auditoría histórica, TruffleHog se ejecutó correctamente mediante Docker
Desktop después de seleccionar el contexto `desktop-linux`. El comando fue:

```bash
docker run --rm -v "${PWD}:/pwd" trufflesecurity/trufflehog:latest \
  git file:///pwd --only-verified --json
```

Resultado: 0 secretos verificados y 0 secretos no verificados. La revisión manual
también confirmó que no existen objetos versionados `.env` reales ni
`secrets/*.txt`.

## 6. Remediación obligatoria

1. Eliminar fallbacks conocidos y hacer que el arranque falle explícitamente si falta
   un secreto.
2. Completar `.env.example` sin valores reales.
3. Corregir `.gitignore` para todas las variantes `.env` y mantener `secrets/` excluido.
4. Crear `secrets.example/` con marcadores únicamente.
5. Migrar todas las credenciales sensibles a Docker Compose Secrets.
6. Leer `/run/secrets/<nombre>` con fallback controlado a variable de entorno.
7. Documentar y ejecutar rotación del JWT y las credenciales detectadas.
8. Integrar detección de secretos en CI.

## 7. Estado de cierre del Punto 1

- Investigación previa: completa.
- Auditoría manual: completa.
- Auditoría automatizada del estado: completa y clasificada.
- Evidencia en README y archivos de reto 9: completa.
- Remediación de Nivel 1/2: pendiente de ejecución, fuera del alcance del criterio 1.
- `.env` real o `secrets/*.txt` versionados en el historial: no encontrados.

Resultado: **Punto 1 cerrado a nivel de auditoría y diagnóstico**.
