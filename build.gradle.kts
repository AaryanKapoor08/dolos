/*
 * Dolos monorepo — root build.
 *
 * Intentionally thin. All shared build behaviour lives in convention plugins under
 * `build-logic/` (applied per-module). Per-module concerns live in each module's
 * own build.gradle.kts. Do NOT add cross-cutting `allprojects {}` / `subprojects {}`
 * blocks here — use convention plugins instead (this is the modern Gradle guidance
 * and keeps module builds explicit about what they apply).
 */

description = "Dolos — Financial Crime Detection & Compliance Platform (monorepo root)"
