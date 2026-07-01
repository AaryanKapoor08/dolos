/*
 * reporting-service — nightly regulatory reporting (Phase 6B).
 *
 * A Spring Batch application that files the FORMAL SAR/STR reports (vs. the ai-copilot's Phase-4
 * DRAFT). A chunked job reads HIGH-severity alerts from the alert read model, a processor builds a
 * SAR/STR narrative, and a composite writer renders each report to MinIO AND upserts a row into the
 * service's own Flyway-managed `reporting` schema (which also holds the Spring Batch metadata tables).
 * Scheduled nightly; idempotent (deterministic keys + ON CONFLICT upsert, and Batch won't re-run a
 * completed business date) and restartable (a failed date re-launches on the same identifying params).
 */
plugins {
    id("dolos.spring-conventions")
}

dependencies {
    implementation(project(":libs:dolos-common"))
    implementation(project(":libs:dolos-security"))

    // Spring Cloud edge (Phase 5A): register with Eureka + pull centralized config from config-server.
    implementation(platform(libs.spring.cloud.bom))
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.spring.cloud.starter.config)

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Batch reporting (Phase 6B): the chunked SAR/STR filing job + JDBC-backed job repository.
    implementation("org.springframework.boot:spring-boot-starter-batch")

    // Persistence: plain JDBC + a Flyway-managed `reporting` schema (Batch metadata + filed_report).
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Render the filed reports to MinIO (S3-compatible object store) — same SDK the copilot uses.
    implementation(libs.minio)

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.batch:spring-batch-test")
}
