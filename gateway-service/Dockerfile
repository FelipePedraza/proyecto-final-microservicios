FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 appgroup \
    && useradd --uid 10001 --gid appgroup --home-dir /app --shell /usr/sbin/nologin --no-create-home appuser
COPY --from=build --chown=appuser:appgroup /build/target/gateway-service-1.0.0.jar app.jar
USER appuser
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
