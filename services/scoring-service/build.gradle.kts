/*
 * scoring-service — risk scoring on the event backbone (Phase 1D).
 *
 * Consumes TransactionReceived, applies a deliberately simple in-code rule, and publishes a
 * RiskScored event. v0 is intentionally stateless/naive (single-transaction, no history) — that
 * limitation is what motivates the Kafka Streams + Drools upgrade in Phase 2. No database here.
 */
plugins {
    id("dolos.spring-conventions")
}

dependencies {
    implementation(project(":libs:dolos-events"))

    // Web is present only so Actuator can serve /actuator/health over HTTP (compose healthcheck).
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Event backbone: consume TransactionReceived, produce RiskScored.
    implementation("org.springframework.kafka:spring-kafka")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
