/*
 * config-server — Spring Cloud Config server (Phase 5A).
 *
 * Serves centralized configuration from deploy/config-repo (the `native` filesystem backend —
 * see application.yml). Registers itself with Eureka so it is discoverable like any other service.
 * Stateless: no database, no Flyway.
 */
plugins {
    id("dolos.spring-conventions")
}

dependencies {
    // Align all spring-cloud-* artifacts to the 2024.0.x train (pairs with Boot 3.4.x).
    implementation(platform(libs.spring.cloud.bom))

    // The Config server + Eureka client (so the registry lists it like every other service).
    implementation(libs.spring.cloud.config.server)
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
