/*
 * alert-service — turns risk scores into alerts (Phase 1E).
 *
 * Consumes RiskScored; when a score crosses the configured threshold it persists an Alert
 * (JPA + its own Flyway-managed `alert` schema), publishes AlertRaised, and exposes a paged,
 * risk-sorted GET /api/alerts. Idempotent on transactionId so a redelivery can't double-alert.
 * Persistence is blocking JDBC, so the Kafka listener runs on virtual threads.
 */
plugins {
    id("dolos.spring-conventions")
}

dependencies {
    implementation(project(":libs:dolos-common"))
    implementation(project(":libs:dolos-events"))
    // Synchronous scoring contract (Phase 2C): alert-service is the gRPC client.
    implementation(project(":libs:dolos-proto"))
    runtimeOnly("io.grpc:grpc-netty-shaded:1.68.1")

    // Resilience4j around the gRPC call: circuit breaker + retry + fallback (needs AOP for the annotations).
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Event backbone: consume RiskScored, produce AlertRaised.
    implementation("org.springframework.kafka:spring-kafka")

    // Persistence: JPA/Hibernate + Flyway-managed `alert` schema on PostgreSQL.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // In-process gRPC server to exercise the ScoreDetailClient against a real channel (Phase 2C).
    testImplementation("io.grpc:grpc-inprocess:1.68.1")
}
