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
include("libs:dolos-proto")
include("libs:dolos-security")
// Phase 5A — Spring Cloud edge: service registry + centralized config server.
include("services:discovery-server")
include("services:config-server")
// Phase 5B — secured reactive API gateway (edge routing + JWT + circuit breakers).
include("services:api-gateway")
// Phase 5C/5D — live push (WebSocket/STOMP) + the GraphQL BFF aggregating the platform.
include("services:notification-service")
include("services:bff-service")
include("services:transaction-service")
include("services:ingestion-service")
include("services:scoring-service")
include("services:graph-service")
include("services:alert-service")
include("services:case-service")
include("services:ai-copilot-service")
// Phase 6B — batch reporting: nightly Spring Batch SAR/STR filing job -> MinIO.
include("services:reporting-service")
// Phase 6D — enterprise integration: a code-defined Apache Camel EIP route ingesting a legacy
// fixed-width partner feed and translating it to the canonical TransactionReceived event.
include("services:legacy-adapter-service")
include("tests:slice-e2e")
// Phase 6E — monorepo-wide architecture rules: one ArchUnit suite enforcing module isolation,
// layering, boundary, and coding conventions across every service + lib.
include("tests:architecture")
