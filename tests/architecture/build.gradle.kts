/*
 * architecture — monorepo-wide ArchUnit rules (Phase 6E).
 *
 * A single test module that imports EVERY service + lib's production classes and enforces the
 * cross-cutting architecture invariants in one shared place (replacing the ad-hoc per-service
 * ArchUnit baselines from Phase 1F): module isolation, controller→service→repository layering,
 * DTO-at-the-boundary, the scoring transport-independence boundary, and coding conventions
 * (constructor injection, slf4j not JUL, no standard streams).
 *
 * ArchUnit analyzes BYTECODE, so it only needs each module's compiled classes on the test runtime
 * classpath — not their (conflicting) transitive dependency graphs. The modules are therefore added as
 * NON-TRANSITIVE testRuntimeOnly dependencies: their jars are scanned, references to framework types
 * (Spring/Camel/Axon/…) resolve to ArchUnit stubs, and we avoid dragging every BOM + its capability
 * conflicts (e.g. camel's lz4 fork vs. kafka's lz4) onto one classpath. The test itself compiles
 * against archunit only (provided by dolos.java-conventions).
 */
plugins {
    id("dolos.java-conventions")
}

// Every Dolos module — analyzed for module isolation + layering + conventions. Non-transitive so only
// the com.dolos.* classes land on the classpath, not each module's framework dependencies.
val analyzedModules =
    listOf(
        ":libs:dolos-common",
        ":libs:dolos-events",
        ":libs:dolos-proto",
        ":libs:dolos-security",
        ":services:discovery-server",
        ":services:config-server",
        ":services:api-gateway",
        ":services:notification-service",
        ":services:bff-service",
        ":services:transaction-service",
        ":services:ingestion-service",
        ":services:scoring-service",
        ":services:graph-service",
        ":services:alert-service",
        ":services:case-service",
        ":services:ai-copilot-service",
        ":services:reporting-service",
        ":services:legacy-adapter-service",
    )

dependencies {
    analyzedModules.forEach { module ->
        testRuntimeOnly(project(module)) { isTransitive = false }
    }
}
