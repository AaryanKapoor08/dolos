/*
 * graph-service — the fraud graph (Phase 2D–2E).
 *
 * Consumes TransactionReceived and MERGEs the account/customer/device graph into Neo4j via
 * Cypher (Spring Data Neo4j), exposes GET /api/graph/account/{id}/neighborhood, and (Phase 2E)
 * runs variable-length Cypher to detect mule rings, publishing RingDetected. The Kafka listener
 * does blocking Bolt I/O, so it runs on virtual threads.
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

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Graph store: Spring Data Neo4j (Neo4jClient + repositories) over the Bolt driver.
    implementation("org.springframework.boot:spring-boot-starter-data-neo4j")

    // Event backbone: consume TransactionReceived, produce RingDetected (Phase 2E).
    implementation("org.springframework.kafka:spring-kafka")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Real Neo4j in a container for the seeded-ring detection test (Phase 2E).
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:neo4j:1.21.3")
}
