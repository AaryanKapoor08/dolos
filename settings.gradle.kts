/*
 * Dolos monorepo — settings.
 *
 * - `build-logic` is an included build that contributes our convention plugins
 *   (dolos.java-conventions, dolos.spring-conventions). It lives in pluginManagement
 *   so those plugins are resolvable by every module.
 * - The foojay toolchain resolver lets Gradle auto-provision the Java 21 toolchain
 *   even though the machine may have a different JDK installed.
 * - Repositories are declared centrally here (FAIL_ON_PROJECT_REPOS); modules must
 *   not declare their own.
 */

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "dolos"

// --- Modules ---
include("libs:dolos-common")
include("libs:dolos-events")
include("services:transaction-service")
// Added as they are created:
// include("libs:dolos-proto")
// include("libs:dolos-security")
