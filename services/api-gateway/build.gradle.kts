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
    // Phase 6G — GraalVM native image. On the classpath but applied ONLY for the native build (`-Pnative`,
    // set by Dockerfile.native). Applying it switches the bootJar/test to Spring Boot AOT mode, so gating it
    // keeps the normal `.\gradlew build` — and CI — byte-for-byte identical to before 6G (no AOT, no
    // build-time context refresh, no slowdown). Version paired with Spring Boot 3.4.x.
    id("org.graalvm.buildtools.native") version "0.10.4" apply false
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

// Phase 6G — with the GraalVM Native Image plugin applied, `bootBuildImage` builds a NATIVE container
// image via the Paketo native buildpack (which brings its own GraalVM, so no local native-image toolchain
// is needed). Tag it distinctly so it sits next to — not on top of — the JVM image used by compose.
// Native image (6G) — opt-in via `-Pnative`. The image is built through `services/api-gateway/
// Dockerfile.native` (Docker CLI / BuildKit), NOT `bootBuildImage`: Spring Boot 3.4.1's embedded buildpack
// Docker client (API v1.24) is rejected with HTTP 400 by Docker Engine 29. That Dockerfile runs
// `./gradlew :services:api-gateway:nativeCompile -Pnative` inside a GraalVM container.
if (project.hasProperty("native")) {
    apply(plugin = "org.graalvm.buildtools.native")
    configure<org.graalvm.buildtools.gradle.dsl.GraalVMExtension> {
        binaries.named("main") {
            imageName.set("api-gateway")
            // Force a standalone EXECUTABLE. Without an explicit main class native-image finds no entry
            // point and silently falls back to shared-library (.so) mode, which produces no runnable binary.
            mainClass.set("com.dolos.gateway.ApiGatewayApplication")
            sharedLibrary.set(false)
            // Cap the native-image builder's own heap so the compile fits the ~6.6 GiB Docker VM without
            // being OOM-killed (the builder otherwise auto-sizes to ~80% of host memory). Peak RSS ~4.4 GB.
            buildArgs.add("-J-Xmx4g")
        }
    }
}
