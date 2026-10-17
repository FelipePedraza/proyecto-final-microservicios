# Guía de Implementación: Punto 1 - Configuración de Jenkins en Docker Compose

## Estrategia Seleccionada
- **Docker Socket Mount:** Se compartirá `/var/run/docker.sock` del host con el contenedor de Jenkins.
- **Setup Wizard Desactivado:** Se usará `JAVA_OPTS=-Djenkins.install.runSetupWizard=false` debido a que los plugins requeridos se instalan desde el `Dockerfile`.

---

## Paso 1: Crear la estructura de carpetas y el Dockerfile

Crea una carpeta llamada `jenkins` en la raíz del proyecto y dentro de ella crea el archivo `Dockerfile`:

### Archivo: `jenkins/Dockerfile`
```dockerfile
FROM jenkins/jenkins:lts-jdk17

# 1. Pre-instalar plugins necesarios
RUN jenkins-plugin-cli --plugins \
    docker-pipeline \
    docker-workflow \
    pipeline-stage-view \
    workflow-aggregator \
    git \
    configuration-as-code \
    job-dsl \
    sonar \
    junit \
    coverage \
    cucumber-reports \
    htmlpublisher

# 2. Instalar el cliente de Docker
USER root
RUN apt-get update && \
    apt-get install -y docker.io && \
    rm -rf /var/lib/apt/lists/*

# 3. Configurar permisos del usuario jenkins para el grupo docker
RUN groupadd -f docker && usermod -aG docker jenkins

USER jenkins
```

---

## Paso 2: Actualizar el `docker-compose.yml`

Agrega el servicio `jenkins` a tu archivo `docker-compose.yml` principal:

```yaml
version: '3.8'

services:
  # ... tus otros servicios ...

  jenkins:
    build:
      context: ./jenkins
      dockerfile: Dockerfile
    container_name: jenkins
    ports:
      - "9090:8080"   # Interfaz web accesible en http://localhost:9090
      - "50000:50000" # Puerto para agentes de Jenkins
    environment:
      - JAVA_OPTS=-Djenkins.install.runSetupWizard=false
    volumes:
      - jenkins_data:/var/jenkins_home
      - /var/run/docker.sock:/var/run/docker.sock # Acceso al Daemon Docker del host
    networks:
      - microservices-network

volumes:
  jenkins_data:

networks:
  microservices-network:
    driver: bridge
```

---

## Paso 3: Permisos del Socket Docker (Host Linux/macOS)

Si ejecutas en un entorno Linux donde el usuario `jenkins` dentro del contenedor no tiene permisos para leer `/var/run/docker.sock`, ejecuta temporalmente en el host:

```bash
sudo chmod 666 /var/run/docker.sock
```

---

## Paso 4: Levantar Jenkins

Ejecuta el siguiente comando en la raíz de tu proyecto:

```bash
docker compose up -d --build jenkins
```

Navega en tu navegador web a: **`http://localhost:9090`**

---

## Paso 5: Crear el Pipeline de Verificación (Smoke Test)

1. En la interfaz web de Jenkins (`http://localhost:9090`), haz clic en **New Item** / **Nueva Tarea**.
2. Ingresa el nombre `test-docker-pipeline` y selecciona **Pipeline**.
3. En la sección **Pipeline Script**, pega el siguiente código:

```groovy
pipeline {
    agent any
    stages {
        stage('Verificación') {
            steps {
                echo 'Jenkins está correctamente configurado'
                sh 'docker --version'
                sh 'docker ps'
            }
        }
    }
}
```

4. Haz clic en **Save** / **Guardar** y luego en **Build Now** / **Construir ahora**.
5. Verifica en el log de consola del Build que tanto `docker --version` como `docker ps` se ejecuten exitosamente y sin errores de permisos.