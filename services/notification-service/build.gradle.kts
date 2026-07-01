/*
 * notification-service — real-time push to the Investigator Console (Phase 5C).
 *
 * A stateless Spring MVC service that consumes the platform's outcome events (AlertRaised, CaseOpened,
 * CaseEscalated, CaseClosed) off Kafka and fans them out to subscribed browsers over WebSocket/STOMP
 * (`/topic/alerts`, `/topic/cases`). No database — it holds nothing; it is a live bridge from the event
 * backbone to the UI. Kafka consumers are idempotent (a redelivery just re-pushes the same frame) and
 * run on virtual threads, matching house style. Registers with Eureka + pulls centralized config, and
 * is a Keycloak resource server (the STOMP handshake at /ws is left open for the browser — see the
 * security config), like every other service.
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

    // Resource-server security (Phase 5B/5C): shared Keycloak JWT chain; this service opens /ws.
    implementation(project(":libs:dolos-security"))

    // WebSocket + STOMP broker (brings spring-boot-starter-web transitively — servlet MVC app).
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Event backbone: consume the outcome events and fan them out over STOMP.
    implementation("org.springframework.kafka:spring-kafka")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
