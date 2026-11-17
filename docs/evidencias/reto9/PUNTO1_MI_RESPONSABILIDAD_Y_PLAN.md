# Reto 9 - Mi responsabilidad para iniciar el Punto 1

Responsable: **Daniel Eduardo Jurado (Dan17i)**  
Alcance inicial: **Punto 1 — Auditoría y diagnóstico**  
Zona propia previa: `vacaciones-service/` y base BDD compartida. En el Reto 9,
el trabajo inicial es de documentación y auditoría; no se toca el código ajeno
sin autorización explícita.

## 1. Objetivo inmediato

Completar el primer criterio de evaluación antes de implementar:

- Investigación conceptual del Reto 9.
- Auditoría manual de los secretos del ecosistema completo.
- Auditoría automatizada del estado y del historial.
- Evidencia de hallazgos y remediación para el README.

No se generan cambios de ejecución ni se crean secretos reales en esta fase.

## 2. Contexto reciente que ya hay que considerar

Lectura de los 12 commits más recientes:

- `52aa1c0`: se implementaron Jenkins/pipelines en todos los servicios.
- `6bd2e8c`: ajuste del pipeline de `departamento-service`.
- `a00cb44`: eliminación de un perfil del Jenkins en `docker-compose.yml`.
- `e624c8d` y `90554d0`: correcciones de pipelines E2E.
- `64da058`, `a146657`, `80a064b`, `0f8683c`, `bf6381f`, `4a4db84` y
  `6335926`: ajustes de Jenkins, volúmenes y Docker Compose.

Implicación: Jenkins, Sonar, Docker, los pipelines y el compose compartido son
parte directa de la superficie sensible y deben incluirse en la auditoría.

## 3. Inventario inicial que me corresponde levantar

Aunque la tabla final debe cubrir todo el ecosistema, yo puedo preparar el
inventario y validar los puntos siguientes sin alterar los servicios:

| Área | Acción inicial |
|---|---|
| Compose raíz | Revisar variables, servicios, secretos reales y referencias al `.env` |
| `.env.example` | Verificar que todas las variables sensibles estén documentadas |
| `.gitignore` | Verificar `.env`, variantes locales y `secrets/` |
| `vacaciones-service` | Identificar DB, RabbitMQ y fallbacks usados por mi servicio |
| Jenkins y herramientas | Inventariar Jenkins, Sonar y Docker/GitHub/credenciales configuradas |
| Observabilidad | Inventariar contraseña de Grafana y cualquier webhook o clave |
| E2E | Inventariar contraseña administrativa y secreto JWT usado por pruebas |
| Historial Git | Ejecutar la auditoría automatizada sin mostrar valores encontrados |

Los detalles de implementación de auth, empleado, departamento, perfiles,
notificaciones y el resto de servicios deben coordinarse con sus responsables;
no los modificaré ni documentaré como una ejecución propia sin evidencia.

## 4. Hallazgos observados antes de implementar

No se muestran valores reales ni secretos locales:

- `.env.example` ya existe y tiene variables sensibles vacías.
- `.env` real existe localmente y está excluido por `.gitignore`; no se leerá ni
  copiará a evidencia ni documentación.
- `docker-compose.yml` usa variables obligatorias, pero no la sección `secrets:`.
- Seis bases de datos tienen variables de contraseña independientes.
- RabbitMQ tiene usuario, contraseña y cookie Erlang.
- JWT se comparte entre auth y gateway.
- Jenkins usa usuario, contraseña y token de Sonar.
- Grafana usa usuario y contraseña administrativos.
- Sonar usa PostgreSQL propio y posible token de integración.
- Hay fallbacks con valores por defecto en vacaciones, notificaciones y
  departamento; requieren que el equipo acuerde y documente la migración.

## 5. Tareas concretas del Punto 1

### Paso A — Investigación

Redactar o validar en el README raíz:

- Twelve-Factor App Factor III.
- Diferencia entre variable de entorno y archivo secreto.
- `.env` frente a `.gitignore`.
- `secrets:` de Docker Compose y `/run/secrets/`.
- Rotación de secretos.

### Paso B — Auditoría manual

Preparar una tabla con todas las filas relevantes:

| Servicio | Secreto | Ubicación actual | Mecanismo objetivo |

Cubrir al menos:

- Los seis microservicios con base de datos.
- auth y gateway por JWT.
- RabbitMQ.
- Grafana.
- Jenkins.
- Sonar.
- E2E.
- Webhook u SMTP si se usa.

No repetir el ejemplo del PDF con solo una o dos filas.

### Paso C — Auditoría automatizada

Ejecutar una herramienta y conservar evidencia sin publicar secretos:

```bash
pip install detect-secrets
detect-secrets scan > .secrets.baseline
detect-secrets audit .secrets.baseline
```

O, como alternativa:

```bash
docker run --rm -v "$PWD:/pwd" trufflesecurity/trufflehog:latest \
  git file:///pwd --only-verified
```

Registrar en el README:

- Comando ejecutado.
- Cantidad y tipo de hallazgos.
- Archivos o commits relacionados sin reproducir los valores.
- Remediación realizada o pendiente.
- Si algún secreto real estuvo en Git, declaración de compromiso permanente y
  necesidad de rotarlo.

### Paso D — Evidencia

Crear o actualizar solamente artefactos propios y acordados:

- Este plan y el resumen general en `docs/evidencias/reto9/`.
- La tabla y el escaneo automatizado en el README raíz, previa autorización,
  porque es un archivo compartido.
- `PROGRESS.md` con estado y resultado de la auditoría.

No se ejecuta una migración de `docker-compose.yml`, servicios o secretos reales
durante el Punto 1.

## 6. Antes de empezar la implementación

Necesito confirmación explícita de Daniel para editar un archivo compartido como
el README raíz o `docker-compose.yml`.

La validación inicial no requiere tocar código ajeno:

```bash
git --no-pager status --short
git --no-pager diff --check
docker compose config --services
detect-secrets scan
```

Si `docker compose config` exige una variable real del `.env`, no debo completar
ni registrar esa variable en documentación. Se reporta únicamente el bloqueo y
se genera el valor localmente.

## 7. Estado actual

- **Plan del reto 9:** creado.
- **Responsabilidad de Punto 1:** definida.
- **Implementación:** pendiente.
- **Auditoría manual:** pendiente de ejecución formal.
- **Auditoría automatizada:** pendiente de ejecución formal.
- **Cambio de código:** ninguno.

## 8. Orden recomendado

1. Confirmar la asignación del Punto 1 con el equipo.
2. Obtener permiso de Daniel para editar el README raíz compartido.
3. Ejecutar el escaneo sin mostrar ni copiar secretos a archivos o chats.
4. Preparar la tabla completa.
5. Registrar hallazgos y remediaciones.
6. Pasar al diseño del Nivel 1 y acordar quién modifica cada microservicio.
