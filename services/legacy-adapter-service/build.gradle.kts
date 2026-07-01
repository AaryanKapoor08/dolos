/*
 * legacy-adapter-service — code-defined enterprise integration (Phase 6D).
 *
 * A single Apache Camel route ingests a LEGACY PARTNER FEED: it polls a bind-mounted inbox for
 * fixed-width files, splits each into records, runs a content-based router that TRANSLATES every
 * valid record into the canonical `TransactionReceived` event (dead-lettering the malformed ones),
 * and produces the result to Redpanda's `transactions.received` topic — the same entry point the
 * reactive ingestion-service (and the visual NiFi flow, Phase 1G) feed. This is the CODE-DEFINED
 * integration; NiFi is the visual one. Both light up scoring -> alert -> case downstream.
 *
 * The service exposes only an actuator surface (health for the Docker probe, prometheus for the 6A
 * scrape), so it applies `dolos-security` for a consistent open-actuator / authenticated-everything-else
 * posture and `dolos.spring-conventions` for the auto-wired tracing + metrics exporters.
 */
plugins {
    id("dolos.spring-conventions")
}

dependencies {
    implementation(project(":libs:dolos-common"))
    implementation(project(":libs:dolos-security"))
    // The canonical event contract + centralized topic names the route translates the feed into.
    implementation(project(":libs:dolos-events"))

    // Spring Cloud edge (Phase 5A): register with Eureka + pull centralized config from config-server.
    implementation(platform(libs.spring.cloud.bom))
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.spring.cloud.starter.config)

    // A minimal servlet surface: only actuator (health probe + Prometheus scrape). Makes this a SERVLET
    // app so DolosSecurityAutoConfiguration (not the reactive one) applies.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Apache Camel (Phase 6D). The BOM aligns every camel-*-starter; starters omit their own version.
    implementation(platform(libs.camel.spring.boot.bom))
    implementation("org.apache.camel.springboot:camel-spring-boot-starter")
    implementation("org.apache.camel.springboot:camel-file-starter")   // poll the bind-mounted inbox
    implementation("org.apache.camel.springboot:camel-kafka-starter")  // produce to Redpanda
    // Bridges Camel's routing engine onto Micrometer Observation, so each exchange opens a span AND the
    // Kafka producer injects the W3C `traceparent` header — joining the 6A distributed trace (Tempo).
    implementation("org.apache.camel.springboot:camel-observation-starter")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.camel:camel-test-spring-junit5")
}
