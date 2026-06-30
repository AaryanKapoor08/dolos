/*
 * discovery-server — Netflix Eureka service registry (Phase 5A).
 *
 * The first Spring Cloud consumer in the monorepo, so it imports the Spring Cloud BOM
 * (the convention plugin deliberately does not). Stateless: no database, no Flyway. Every
 * other service registers here as a Eureka client so the gateway can route by `lb://`.
 */
plugins {
    id("dolos.spring-conventions")
}

dependencies {
    // Align all spring-cloud-* artifacts to the 2024.0.x train (pairs with Boot 3.4.x).
    implementation(platform(libs.spring.cloud.bom))

    // The Eureka server itself (brings in its embedded dashboard at the context root).
    implementation(libs.spring.cloud.starter.netflix.eureka.server)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Structured (JSON) logging — see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
