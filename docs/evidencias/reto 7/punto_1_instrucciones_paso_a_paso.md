# Reto 7 - Infraestructura Jenkins como codigo (Punto 1)

Esta guia describe la configuracion actual del repositorio. No es necesario crear manualmente los servicios ni los jobs.

## Componentes actuales

- `docker-compose.yml` ejecuta Jenkins en `http://localhost:9090`, expone el puerto de agentes `50000` y monta `/var/run/docker.sock` para Docker-out-of-Docker.
- `jenkins/Dockerfile` parte de Jenkins LTS con JDK 21, instala el cliente Docker/Compose y preinstala los plugins de pipeline, JUnit, cobertura, Cucumber, HTML y SonarQube.
- `jenkins/casc.yaml` provisiona el usuario desde `JENKINS_ADMIN_USERNAME` y `JENKINS_ADMIN_PASSWORD`, la credencial `sonarqube-token`, el servidor SonarQube y los jobs `auth-service`, `empleado-service` y `test-docker-pipeline`.
- Jenkins y los agentes Docker usan la red `microservicios-network`. En Linux, `DOCKER_GID` debe coincidir con el grupo del socket; en Docker Desktop el ejemplo usa `0`.

## Arranque

1. En la raiz del repositorio, copia `.env.example` como `.env` y cambia las contrasenas de ejemplo.
2. Levanta SonarQube para completar su configuracion inicial y genera un token. Guarda el token en `SONAR_TOKEN` en `.env`; no lo versionees.
3. Levanta el entorno con `docker compose up -d --build`. Revisa el arranque con `docker compose ps` y `docker compose logs jenkins`.
4. Ingresa a `http://localhost:9090` con los valores `JENKINS_ADMIN_USERNAME` y `JENKINS_ADMIN_PASSWORD` de `.env`. El setup wizard esta desactivado y JCasC provisiona ese usuario; no se usa `initialAdminPassword`.
5. Confirma que aparecen los jobs definidos en JCasC. `test-docker-pipeline` comprueba `docker --version` y `docker ps`; ejecutalo desde la interfaz y revisa el log de consola.

Los pipelines de aplicacion se ejecutan desde `auth-service` y `empleado-service` con **Build Now**. La instancia SonarQube requiere que se creen/verifiquen el Quality Gate de cobertura y el webhook indicados en la [guia del Reto 7](../../README.md#reto-7-integracion-continua-con-jenkins-y-sonarqube).

## Limitaciones del acceso al socket

El socket Docker concede control elevado sobre el daemon del host. No uses `chmod 666 /var/run/docker.sock`; configura el GID correcto y ejecuta este entorno solo en una maquina de desarrollo controlada.
