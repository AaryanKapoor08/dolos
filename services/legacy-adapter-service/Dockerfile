# syntax=docker/dockerfile:1
#
# legacy-adapter-service — layered Spring Boot image (Phase 6D).
# Build context is the REPO ROOT (see deploy/docker-compose/services.yml) so the Gradle
# multi-module build can resolve build-logic + libs (dolos-common, dolos-security, dolos-events).

# --- Build stage: produce the layered boot jar with the Gradle wrapper ---
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY build-logic build-logic
COPY libs libs
COPY services/legacy-adapter-service services/legacy-adapter-service
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && \
    ./gradlew :services:legacy-adapter-service:bootJar --no-daemon -x test

# --- Extract layers from the boot jar for better runtime caching ---
FROM eclipse-temurin:21-jre-jammy AS extract
WORKDIR /app
COPY --from=build /workspace/services/legacy-adapter-service/build/libs/*.jar app.jar
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
EXPOSE 8094
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
