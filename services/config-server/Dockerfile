# syntax=docker/dockerfile:1
#
# config-server — layered Spring Boot image (Phase 5A).
# Build context is the REPO ROOT (see deploy/docker-compose/services.yml) so the Gradle
# multi-module build can resolve build-logic + the version catalog. The config-repo itself
# is NOT baked in — it is bind-mounted at runtime (see services.yml) so edits + /actuator/refresh
# pick up changes without a rebuild.

# --- Build stage: produce the layered boot jar with the Gradle wrapper ---
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY build-logic build-logic
COPY libs libs
COPY services/config-server services/config-server
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && \
    ./gradlew :services:config-server:bootJar --no-daemon -x test

# --- Extract layers from the boot jar for better runtime caching ---
FROM eclipse-temurin:21-jre-jammy AS extract
WORKDIR /app
COPY --from=build /workspace/services/config-server/build/libs/*.jar app.jar
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
EXPOSE 8888
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
