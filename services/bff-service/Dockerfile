# syntax=docker/dockerfile:1
#
# bff-service — layered Spring Boot image (Phase 5D).
# Build context is the REPO ROOT (see deploy/docker-compose/services.yml) so the Gradle
# multi-module build can resolve build-logic + the version catalog + the libs it depends on.

# --- Build stage: produce the layered boot jar with the Gradle wrapper ---
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY build-logic build-logic
COPY libs libs
COPY services/bff-service services/bff-service
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && \
    ./gradlew :services:bff-service:bootJar --no-daemon -x test

# --- Extract layers from the boot jar for better runtime caching ---
FROM eclipse-temurin:21-jre-jammy AS extract
WORKDIR /app
COPY --from=build /workspace/services/bff-service/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract --destination extracted

# --- Runtime ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 dolos
COPY --from=extract /app/extracted/dependencies/ ./
COPY --from=extract /app/extracted/spring-boot-loader/ ./
COPY --from=extract /app/extracted/snapshot-dependencies/ ./
COPY --from=extract /app/extracted/application/ ./
USER dolos
EXPOSE 8092
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
