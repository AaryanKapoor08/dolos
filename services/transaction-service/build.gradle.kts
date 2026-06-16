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

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
