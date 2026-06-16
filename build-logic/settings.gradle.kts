/*
 * build-logic — an included build providing Dolos convention plugins.
 * It needs its own repositories to resolve the Gradle plugins it wraps
 * (Spring Boot, dependency-management), and the foojay resolver so it can
 * provision the Java 21 toolchain it compiles against (matching the main build).
 */
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "build-logic"
