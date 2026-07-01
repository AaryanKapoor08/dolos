# api-gateway — GraalVM native image (Phase 6G)

The api-gateway can be built as a **GraalVM native image**: an ahead-of-time-compiled, standalone Linux
executable with no JVM. This records how it's built and the startup / memory / size delta vs the JVM build.

## Why the gateway

It's the platform's single edge — the first thing to start and the one instance that's always running —
so a sub-second, low-footprint edge is the most valuable place to spend the native-image effort. It's also
the hardest Spring app here to native-compile (reactive Spring Cloud Gateway + reactive Spring Security +
Eureka + Resilience4j + config client), which makes it the honest test of the toolchain.

## How it's built

The native build is **opt-in and off the normal build graph** — `.\gradlew build` is byte-for-byte
identical to before 6G (no AOT processing, no build-time context refresh). Applying the GraalVM plugin
switches the bootJar/test to Spring Boot AOT mode, so it's gated behind `-Pnative`:

```kotlin
// build.gradle.kts
plugins {
    id("org.graalvm.buildtools.native") version "0.10.4" apply false   // classpath only
}
if (project.hasProperty("native")) {
    apply(plugin = "org.graalvm.buildtools.native")
    // main binary: explicit mainClass + sharedLibrary=false (else native-image builds a .so, not an exe),
    // and -J-Xmx4g so the compile fits the ~6.6 GiB Docker VM.
}
```

The image is built with a **native Dockerfile via the Docker CLI / BuildKit**, not Spring Boot's
`bootBuildImage`: Spring Boot 3.4.1's embedded buildpack Docker client (Docker API v1.24) is rejected with
**HTTP 400 by Docker Engine 29** (a known bleeding-edge-Docker incompatibility). The Dockerfile runs the
AOT + `native-image` compile inside a `ghcr.io/graalvm/native-image-community:21` container and copies the
resulting executable onto a slim `oraclelinux:9-slim` runtime:

```powershell
docker build -f services/api-gateway/Dockerfile.native -t dolos/api-gateway:native .
```

The native-image compile takes **~1m 44s** and peaks at **~4.4 GB** RSS (capped via `-J-Xmx4g`).

## Measured delta

> **Numbers are machine-relative.** Recorded on **AMD Ryzen 7 8845HS (8C/16T), Windows 11, Docker Desktop
> Linux VM = 6.6 GiB**, GraalVM CE **21.0.2** (native) vs Eclipse Temurin **21** (JVM). Both run as
> standalone containers, **alone**, with identical env (`EUREKA_CLIENT_ENABLED=false`,
> `SPRING_CLOUD_CONFIG_ENABLED=false`) so startup isn't blocked on infra. JVM baseline = the boot jar baked
> into `eclipse-temurin:21-jre-jammy` (mirrors `Dockerfile`).

| Metric              | JVM (`:jvm`)      | Native (`:native`) | Delta            |
|---------------------|-------------------|--------------------|------------------|
| **Startup**         | 5.02 s            | **0.384 s**        | **~13× faster**  |
| **Idle RSS**        | 363 MiB           | **80 MiB**         | **~4.5× less**   |
| **Image size**      | 525 MB            | **327 MB**         | **~1.6× smaller**|
| Build time          | seconds           | ~1m 44s + AOT      | much slower      |

The trade is the classic native-image bargain: you pay a slow, memory-hungry **build** to get a fast,
lean **runtime**. For an always-on edge (and for scale-to-zero / rapid-restart on Kubernetes in 6H), the
runtime side is what matters.

## It actually routes + enforces security (native)

The native container was exercised on `:18081` (Eureka off, so `lb://` targets don't resolve — that's fine,
it proves the edge pipeline without the full stack):

| Request                                    | Result | Proves                                            |
|--------------------------------------------|--------|---------------------------------------------------|
| `GET /actuator/health`                     | 200    | native app serving HTTP over Netty                |
| `GET /api/transactions/1` (no token)       | 401    | reactive OAuth2 resource-server enforced at edge  |
| `OPTIONS /api/transactions/1` (preflight)  | 200    | global CORS config active                         |
| `GET /ingest/x` (open route → `lb://…`)    | 503    | route **matched** → Resilience4j fallback fired (`ApiError` body from `/fallback/ingestion-service`) |

So the native gateway boots sub-second, serves, routes, and enforces JWT + CORS — the full reactive edge,
AOT-compiled. Live `lb://` routing to downstream services is exercised by the compose / k3d stack.

## Notes for the maintainer

- The AOT context refresh logs a `RefreshScope` code-generation failure unless refresh scope is disabled —
  hence `spring.cloud.refresh.enabled=false` in `application.yml` (the gateway imports config as `optional:`
  and never used live `@RefreshScope`, so this costs nothing and keeps JVM/AOT/native on one code path).
- Two benign `native-image` warnings are expected: missing `WebMvc*` reflection metadata (this is a
  **reactive** gateway — no servlet MVC on the classpath) and SLF4J provider notices during AOT.
- If the native-image compile is OOM-killed on a smaller Docker VM, lower `-J-Xmx4g` in `build.gradle.kts`.
