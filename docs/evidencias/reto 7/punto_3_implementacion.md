# Punto 3 - Calidad de código con SonarQube

## Estado

La implementación del Punto 3 quedó completa a nivel de código y configuración reproducible. La ejecución en contenedores y las capturas de interfaz quedan pendientes porque el equipo usado para el desarrollo no dispone actualmente del almacenamiento necesario para descargar y ejecutar las imágenes de Jenkins, SonarQube y PostgreSQL.

Esta limitación no debe presentarse como una ejecución exitosa: todavía falta validar el pipeline en un host con Docker y espacio suficiente.

## Componentes implementados

### Docker Compose

- `sonar-db` usa `postgres:16-alpine`, almacenamiento persistente y healthcheck con `pg_isready`.
- `sonarqube` usa `sonarqube:lts-community`, publica el puerto `9000` y persiste datos, logs y extensiones.
- Las credenciales de PostgreSQL y el token de análisis se reciben desde `.env`.
- La red tiene el nombre estable `microservicios-network`, compartido por Jenkins, SonarQube y los agentes Docker del pipeline.

### Jenkins Configuration as Code

- El token de SonarQube se registra como una credencial `Secret text` con ID `sonarqube-token`.
- El servidor global de SonarQube se registra con el nombre `sonarqube` y URL interna `http://sonarqube:9000`.
- El token real no se almacena en Git; JCasC lo toma de `SONAR_TOKEN`.

### Pipeline de `auth-service`

Después de generar los reportes de pruebas y JaCoCo, el pipeline ejecuta:

1. `SonarQube Analysis`: envía el código y `target/site/jacoco/jacoco.xml` al proyecto `auth-service`.
2. `Quality Gate`: espera hasta cinco minutos la respuesta del webhook y aborta el pipeline si el resultado no es satisfactorio.

El agente Maven se conecta explícitamente a `microservicios-network`; de otro modo, el contenedor efímero no podría resolver el hostname `sonarqube`.

## Decisiones técnicas

- **Token:** Jenkins Credential aprovisionada con JCasC desde una variable local, nunca hardcodeada en el Jenkinsfile.
- **Quality Gate:** personalizado con cobertura mínima de 70 %, según la guía del reto.
- **Cobertura:** se reutiliza el XML de JaCoCo generado en la etapa `Test`; SonarQube no vuelve a ejecutar pruebas.
- **Webhook:** URL interna prevista `http://jenkins:8080/sonarqube-webhook/`.
- **Scanner:** Maven ejecuta el Sonar Maven Plugin dentro del mismo agente usado para compilar y probar.

## Validaciones estáticas realizadas

```bash
git diff --check
docker compose --env-file .env.example config --quiet
```

Ambos comandos finalizaron correctamente. Esto verifica formato del diff, interpolación de variables y estructura válida del archivo Compose, pero no reemplaza la prueba real de los contenedores.

## Validación pendiente en un host con Docker

1. Crear `.env` a partir de `.env.example` y cambiar todas las contraseñas de ejemplo.
2. Levantar `sonar-db` y `sonarqube`.
3. Cambiar la contraseña inicial de SonarQube.
4. Crear el proyecto `auth-service`.
5. Crear y asignar el Quality Gate de cobertura >= 70 %.
6. Generar el token, guardarlo como `SONAR_TOKEN` en `.env` y recrear Jenkins.
7. Crear el webhook hacia Jenkins.
8. Ejecutar el job `auth-service` y comprobar las cinco etapas.
9. Capturar el dashboard, la cobertura, el Quality Gate aprobado y un Quality Gate fallido.

## Resultado esperado

```text
Checkout -> Build -> Test -> SonarQube Analysis -> Quality Gate
                                                    | PASS: pipeline continúa
                                                    | FAIL: pipeline se detiene
```
