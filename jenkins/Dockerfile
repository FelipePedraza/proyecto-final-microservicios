FROM jenkins/jenkins:lts-jdk17

# 1. Pre-instalar plugins necesarios
RUN jenkins-plugin-cli --plugins \
    docker-workflow \
    pipeline-stage-view \
    workflow-aggregator \
    git \
    configuration-as-code \
    job-dsl \
    sonar \
    junit \
    timestamper \
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
