/*
 * transaction-service — the canonical store of record for transactions.
 *
 * Phase 0C (this step): Spring Boot skeleton — Actuator, virtual threads, and structured
 * JSON logging. No persistence yet.
 * Phase 0D: adds JPA + Flyway + REST on top of the Postgres infra baseline.
 */
plugins {
    id("dolos.spring-conventions")
}

dependencies {
    implementation(project(":libs:dolos-common"))
    implementation(project(":libs:dolos-events"))

    // Spring Cloud edge (Phase 5A): register with Eureka + pull centralized config from config-server.
    implementation(platform(libs.spring.cloud.bom))
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.spring.cloud.starter.config)

    // Resource-server security (Phase 5B): every endpoint needs a valid Keycloak JWT; realm roles map
    // to ROLE_*. Brings Spring Security transitively via the shared starter.
    implementation(project(":libs:dolos-security"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Event backbone (Phase 1C): consume TransactionReceived from Kafka/Redpanda and persist
    // the canonical transaction. transaction-service is the canonical store of record.
    implementation("org.springframework.kafka:spring-kafka")

    // Persistence: JPA/Hibernate + Flyway-managed schema on PostgreSQL (Phase 0D).
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Real Postgres in a container for the persistence integration test (Phase 6E). SKIPS locally when
    // Docker is unreachable (disabledWithoutDocker); CI (Linux Docker) runs it for real.
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:postgresql:1.21.3")
}
