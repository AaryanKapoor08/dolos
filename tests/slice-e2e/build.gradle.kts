/*
 * slice-e2e — Phase 1F end-to-end test of the Phase 1 vertical slice.
 *
 * Boots the four slice services (ingestion → transaction + scoring → alert) in-process against
 * real Postgres + Redpanda spun up by Testcontainers, posts a high-amount transaction to ingestion,
 * and asserts an alert appears at alert-service's GET /api/alerts — proving the whole slice wires up.
 *
 * Not a Spring Boot app itself: it depends on the service modules and launches their contexts, so
 * it uses dolos.java-conventions (no Spring Boot plugin / no bootJar) and pulls Spring + Testcontainers
 * versions from BOMs.
 */
plugins {
    id("dolos.java-conventions")
}

dependencies {
    // BOMs so the versionless Spring + Testcontainers test deps below resolve consistently.
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:" + libs.versions.springBoot.get()))
    testImplementation(platform("org.testcontainers:testcontainers-bom:" + libs.versions.testcontainers.get()))

    // Canonical topic names (Topics) + the Kafka AdminClient used to pre-create the pipeline topics
    // before the Streams app boots. These are `implementation` deps of the services, so not exposed
    // transitively to this module's compile classpath — declare them directly.
    testImplementation(project(":libs:dolos-events"))
    testImplementation("org.apache.kafka:kafka-clients")

    // The services under test — booted in-process and wired to the Testcontainers infra.
    testImplementation(project(":services:ingestion-service"))
    testImplementation(project(":services:transaction-service"))
    testImplementation(project(":services:scoring-service"))
    testImplementation(project(":services:alert-service"))

    // Spring Boot runtime to launch the contexts + test utilities (assertj, awaitility, json).
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Real infra in containers (Postgres + Redpanda), matching the compose image versions.
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:redpanda")
}
