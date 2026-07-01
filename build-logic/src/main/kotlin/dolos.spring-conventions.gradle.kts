/*
 * dolos.spring-conventions
 * For Spring Boot services. Builds on dolos.java-conventions and adds the Spring Boot
 * plugin + Spring's dependency-management (which auto-imports the spring-boot-dependencies
 * BOM, so starter versions are managed and omitted from module builds).
 *
 * The Spring Cloud BOM is intentionally NOT imported here yet — it is added when the
 * first Spring Cloud consumer appears. Early services only need Spring Boot + their
 * specific starters.
 */
plugins {
    id("dolos.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // Observability backplane (Phase 6A): ONE wiring point so every Dolos Spring service exports
    // distributed traces + Prometheus metrics. All three versions are managed by the
    // spring-boot-dependencies BOM (imported transitively by io.spring.dependency-management above),
    // so none are pinned here.
    //   - micrometer-tracing-bridge-otel: Micrometer Tracing -> OpenTelemetry SDK bridge (span model).
    //   - opentelemetry-exporter-otlp:    ships spans over OTLP to Tempo (management.otlp.tracing.endpoint).
    //   - micrometer-registry-prometheus: exposes /actuator/prometheus for Prometheus to scrape.
    // Configuration names are quoted strings because the `implementation` type-safe accessor is NOT
    // generated in this precompiled-script plugin (the java plugin is applied indirectly, via
    // dolos.java-conventions, not in this script's own plugins {} block).
    "implementation"("io.micrometer:micrometer-tracing-bridge-otel")
    "implementation"("io.opentelemetry:opentelemetry-exporter-otlp")
    "implementation"("io.micrometer:micrometer-registry-prometheus")
}
