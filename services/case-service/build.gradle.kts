/*
 * case-service — the investigation case management service (Phase 3).
 *
 * Built as a Spring Modulith modular monolith: four application modules
 *   - casecmd     : the event-sourced Case aggregate + commands (Axon command side, Phase 3B)
 *   - casequery   : the CaseView read model + projection (Axon query side, Phase 3C)
 *   - workflow    : the BPMN investigation process (Flowable, Phase 3D — placeholder for now)
 *   - integration : Kafka in/out (AlertRaised -> OpenCase + outbox, Phase 3E — placeholder for now)
 * with module boundaries enforced by ApplicationModules.verify() and documented via the Documenter.
 *
 * 3A scope: the Modulith skeleton + house-style baseline (Actuator, JSON logging, correlation filter).
 * Axon, JPA and Flyway are wired in 3B when the first persistent tables (the event store) appear.
 */
plugins {
    id("dolos.spring-conventions")
}

dependencies {
    // Align all spring-modulith-* artifacts; Boot 3.4 does not manage Modulith itself.
    implementation(platform(libs.spring.modulith.bom))
    // Align all axon-* artifacts (CQRS + Event Sourcing, Phase 3B/3C).
    implementation(platform(libs.axon.bom))

    implementation(project(":libs:dolos-common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring Modulith: module model, runtime support, and the verification/observability core.
    implementation(libs.spring.modulith.starter.core)

    // Axon Framework: the Case aggregate (command side, 3B) + the CaseView projection (query side, 3C).
    // The JPA event store lives on Postgres (decision G) — Axon Server is disabled in application.yml.
    implementation(libs.axon.spring.boot.starter)

    // Persistence: JPA/Hibernate hosts both the Axon event store and the CaseView read model, in
    // case-service's own Flyway-managed `casework` schema on PostgreSQL.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // ApplicationModules verification + scenario support.
    testImplementation(libs.spring.modulith.starter.test)
    // The Documenter (C4/PlantUML module docs) lives in spring-modulith-docs.
    testImplementation(libs.spring.modulith.docs)
    // Axon's AggregateTestFixture for given-when-then aggregate unit tests (no DB, no Spring context).
    testImplementation(libs.axon.test)
}
