/*
 * dolos-security — the shared OAuth2/OIDC resource-server starter (Phase 3F).
 *
 * Applied by a service to become a JWT resource server: it auto-configures a stateless security
 * filter chain (every request authenticated, actuator health/info open), maps Keycloak realm roles to
 * Spring {@code ROLE_*} authorities, and enables method security (@PreAuthorize). Applied to
 * case-service in Phase 3F; to every service behind the gateway in Phase 5.
 *
 * It uses dolos.spring-conventions only for Spring's dependency management (managed Spring Security /
 * Boot versions). It is a LIBRARY, not a runnable app, so the Spring Boot plugin's bootJar is disabled
 * and the plain library jar is published for other modules to depend on.
 */
plugins {
    id("dolos.spring-conventions")
}

// Library, not an application: emit the plain jar, not an executable boot jar.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { enabled = false }
tasks.named<Jar>("jar") { enabled = true }

dependencies {
    // Exposed to consumers (api): applying this starter brings resource-server + security with it.
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    api("org.springframework.boot:spring-boot-starter-security")
    // Reactive edge support (Phase 5B): the reactive auto-config references WebFlux/Reactor types
    // (ServerHttpSecurity, Mono). compileOnly — it must NOT leak WebFlux onto the servlet services that
    // apply this starter via `implementation`; the reactive apps (api-gateway, ingestion) bring WebFlux
    // themselves, and the reactive auto-config only activates in a REACTIVE web app.
    compileOnly("org.springframework.boot:spring-boot-starter-webflux")
    // We provide an auto-configuration; the processor emits its metadata.
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test the role mapping + access rules with mock JWTs (no Keycloak needed locally).
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
