/*
 * build-logic — compiles the Dolos convention plugins (precompiled Kotlin DSL script
 * plugins under src/main/kotlin). To let those scripts apply third-party plugins by id
 * (e.g. `org.springframework.boot`), the corresponding plugin artifacts must be on this
 * build's classpath as dependencies below.
 *
 * Versions here must match gradle/libs.versions.toml (springBoot / springDependencyMgmt).
 */
plugins {
    `kotlin-dsl`
}

// Compile build-logic against a Java 21 toolchain so the Kotlin and Java targets agree
// (avoids the "Inconsistent JVM-target" warning when the daemon JVM is newer than 21).
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.4.1")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:1.1.7")
}
