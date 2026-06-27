/*
 * scoring-service — stateful risk scoring on the event backbone (Phase 2A).
 *
 * A Kafka Streams topology consumes TransactionReceived and maintains windowed state (velocity,
 * structuring) plus an account's last-seen location, builds a ScoringFact, scores it, and publishes
 * an enriched RiskScored. This replaces the v0 stateless consumer (Phase 1D) whose single-transaction
 * blindness motivated the upgrade. No database here — state lives in Kafka Streams stores.
 */
plugins {
    id("dolos.spring-conventions")
}

dependencies {
    implementation(project(":libs:dolos-events"))

    // Web is present only so Actuator can serve /actuator/health over HTTP (compose healthcheck).
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Event backbone: spring-kafka provides @EnableKafkaStreams + the StreamsBuilderFactoryBean;
    // kafka-streams is the topology/state-store library itself (versions managed by the Boot BOM).
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-streams")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // TopologyTestDriver — drives the topology in-process with no broker (Phase 2A unit tests).
    testImplementation("org.apache.kafka:kafka-streams-test-utils")
}
