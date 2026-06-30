/*
 * ingestion-service — the reactive edge of the platform (Phase 1B).
 *
 * Accepts inbound transactions over a non-blocking WebFlux endpoint, stores the raw record
 * reactively via R2DBC, and publishes a TransactionReceived event to Kafka (Redpanda). This
 * is the producer side of the event backbone; transaction/scoring/alert services consume it.
 *
 * Flyway runs schema migrations over a short-lived JDBC connection (spring.flyway.url) — the
 * application itself talks to Postgres only via R2DBC. The JDBC DataSource auto-config is
 * therefore excluded in IngestionServiceApplication.
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

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Event backbone: produce TransactionReceived to Kafka/Redpanda.
    implementation("org.springframework.kafka:spring-kafka")

    // Reactive Postgres driver for the app's data path.
    runtimeOnly("org.postgresql:r2dbc-postgresql")
    // JDBC driver used ONLY by Flyway to run migrations (see application.yml spring.flyway.url).
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}
