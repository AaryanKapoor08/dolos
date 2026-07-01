/*
 * api-gateway - the platform's single secured entry point (Phase 5B).
 *
 * A REACTIVE Spring Cloud Gateway (WebFlux, Netty - NOT servlet MVC): routes API traffic to the
 * business services by logical Eureka name (load-balanced), validates the edge JWT as an OAuth2
 * resource server, relays the bearer token downstream, and wraps each route in a Resilience4j
 * circuit breaker with a local 503 fallback.
 *
 * It registers with Eureka + pulls centralized config like every other service. Stateless: no database,
 * no Flyway. Reactive security comes from libs/dolos-security's DolosReactiveSecurityAutoConfiguration
 * (shared Keycloak realm-role mapping); this module declares its own edge SecurityWebFilterChain.
 *
 * NOTE: Kotlin build-script block comments NEST. A slash-star sequence (as in a route glob written the
 * literal way) opens an inner comment that never closes and silently swallows the plugins block - so
 * describe route paths in words here, and keep glob syntax to line comments and application.yml.
 */
plugins {
    id("dolos.spring-conventions")
}

dependencies {
    // Align all spring-cloud-* artifacts to the 2024.0.x train (pairs with Boot 3.4.x).
    implementation(platform(libs.spring.cloud.bom))

    // The reactive gateway itself (brings spring-boot-starter-webflux + Netty).
    implementation(libs.spring.cloud.starter.gateway)
    // Per-route circuit breakers with Resilience4j on the reactive stack.
    implementation(libs.spring.cloud.starter.circuitbreaker.reactor.resilience4j)
    // Register with Eureka (enables lb:// routing) + pull centralized config.
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.spring.cloud.starter.config)

    // Shared reactive resource-server security (Keycloak realm roles mapped to ROLE_*).
    implementation(project(":libs:dolos-security"))
    // The ApiError envelope for the 503 fallback body (framework-agnostic record).
    implementation(project(":libs:dolos-common"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Structured (JSON) logging - see src/main/resources/logback-spring.xml.
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}
