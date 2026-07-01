/*
 * bench — JMH micro-benchmark harness (Phase 6F).
 *
 * A standalone measurement module, deliberately OUTSIDE the normal build/test graph: the benchmarks
 * live in the `jmh` source set (src/jmh/java) and only run via the explicit `jmh` task, so
 * `./gradlew build` stays fast. It is NOT a Spring Boot app (dolos.java-conventions — no bootJar) and
 * it is NOT imported by tests:architecture, so it is exempt from the monorepo ArchUnit rules.
 *
 * What it measures (see README.md for the committed numbers + interpretation):
 *   1. The Drools rule-session hot path — reuses the REAL RiskScoringEngine + rules/aml.drl from
 *      scoring-service (does not re-implement the rules).
 *   2. Virtual vs platform threads on a blocking workload — the trade-off behind
 *      spring.threads.virtual.enabled=true.
 *
 * Run:  ./gradlew.bat :bench:jmh
 * Results land in build/results/jmh/ (human-readable text + the raw run log).
 */
plugins {
    id("dolos.java-conventions")
    id("me.champeau.jmh") version "0.7.2"
}

jmh {
    // Pin the JMH runtime the plugin provisions (independent of the Boot BOM).
    jmhVersion = "1.37"
    // Sane, reproducible defaults; per-benchmark annotations still override where they set them.
    fork = 1
    warmupIterations = 3
    iterations = 5
    resultFormat = "TEXT"
}

// The self-contained benchmark uber-jar bundles the whole scoring-service dependency tree
// (Spring + Kafka + Drools), which blows past the 65,535-entry classic-zip limit — enable zip64.
tasks.named<Jar>("jmhJar") {
    isZip64 = true
}

dependencies {
    // scoring-service's transitive deps are versionless (managed by the Boot BOM via
    // io.spring.dependency-management, which does NOT publish constraints to consumers). Import the same
    // platform here so they resolve — exactly as tests:slice-e2e does.
    jmh(platform("org.springframework.boot:spring-boot-dependencies:" + libs.versions.springBoot.get()))

    // The real scoring engine + fact type, and the RiskScored return type. The full Drools runtime
    // (mvel/xml-support) + the compiled rules/aml.drl resources arrive transitively from scoring-service.
    jmh(project(":services:scoring-service"))
    jmh(project(":libs:dolos-events"))
    // KieServices / KieContainer on the compile classpath (scoring-service exposes Drools only at runtime).
    jmh(libs.drools.engine)
}
