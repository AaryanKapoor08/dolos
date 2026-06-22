/*
 * dolos.java-conventions
 * Baseline for every Dolos JVM module: Java 21 toolchain, consistent coordinates,
 * and a standard JUnit 5 + Mockito test setup. Repositories come from the root
 * settings (dependencyResolutionManagement), so none are declared here.
 *
 * Note: test dependency versions are hardcoded here because precompiled script
 * plugins cannot read the version catalog directly. Keep them aligned with
 * gradle/libs.versions.toml (junit / mockito / archunit).
 */
plugins {
    `java-library`
}

group = "com.dolos"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    // Architecture tests (Phase 1F baseline → monorepo-wide in Phase 6E). Available to every
    // module so each can enforce its own layering/boundary rules.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Surface problems early; treat deprecation/unchecked as visible warnings.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    // Retain method/constructor parameter names in bytecode so Jackson can bind JSON to
    // record components by name (Kafka event contracts in dolos-events) without annotations.
    options.compilerArgs.add("-parameters")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
