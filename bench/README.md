# bench — JMH micro-benchmarks (Phase 6F)

A standalone [JMH](https://github.com/openjdk/jmh) harness that measures two hot paths in the Dolos
platform and commits the numbers alongside their interpretation. It is deliberately **outside the normal
build/test graph**: the benchmarks live in the `jmh` source set and only run via the explicit `:bench:jmh`
task, so `./gradlew build` stays fast. The module is not imported by `tests:architecture`, so it is exempt
from the monorepo ArchUnit rules.

## Running

```powershell
.\gradlew.bat :bench:jmh
```

Results are written to `bench/build/results/jmh/results.txt` (and echoed to the console). The `jmhVersion`,
fork count, and iteration counts are pinned in `build.gradle.kts`; per-benchmark annotations override the
mode/units where they differ.

## What is measured

1. **`DroolsScoringBenchmark`** — the Drools rule-session hot path. It reuses the **real**
   `RiskScoringEngine` and the **real** `rules/aml.drl` from `scoring-service` (nothing here re-implements
   the rules), so the numbers are the steady-state cost the scoring pipeline actually pays per transaction.
2. **`VirtualThreadBenchmark`** — virtual vs platform threads on a blocking workload. Dolos runs with
   `spring.threads.virtual.enabled=true`; this quantifies what that buys a blocking request handler under
   concurrency, sweeping the block duration to show *when* virtual threads help.

## Results

> ⚠️ **Numbers are machine-relative.** Recorded on:
> **AMD Ryzen 7 8845HS (8 cores / 16 threads), Windows 11, ~14 GB RAM, Eclipse Temurin JDK 21.0.9+10-LTS.**
> JMH 1.37, 1 fork, 5 warmup + 5 measurement iterations of 1 s each. Re-run on your box before quoting.

```
Benchmark                                      (blockMillis)  (taskCount)   Mode  Cnt     Score     Error   Units
DroolsScoringBenchmark.scoreCleanFact                    N/A          N/A  thrpt    5   393.713 ±  54.824  ops/ms
DroolsScoringBenchmark.scoreHighRiskFact                 N/A          N/A  thrpt    5   232.592 ±  49.755  ops/ms
DroolsScoringBenchmark.buildFreshKieContainer            N/A          N/A   avgt    5  1830.005 ± 131.990   ms/op
VirtualThreadBenchmark.platformPool                        1          500  thrpt    5    51.470 ±  14.766   ops/s
VirtualThreadBenchmark.virtualThreads                      1          500  thrpt    5    64.037 ±   1.507   ops/s
VirtualThreadBenchmark.platformPool                       20          500  thrpt    5     3.203 ±   0.036   ops/s
VirtualThreadBenchmark.virtualThreads                     20          500  thrpt    5    32.071 ±   0.150   ops/s
```

## Interpretation

### 1. Drools scoring is cheap; compiling the rules is not

| Path | Throughput | ~ per fire |
|---|---:|---:|
| `scoreCleanFact` (no rule fires) | 393.7 ops/ms | ~2.5 µs |
| `scoreHighRiskFact` (5 typologies fire) | 232.6 ops/ms | ~4.3 µs |
| `buildFreshKieContainer` (compile the DRL) | — | **1830 ms** |

- **Firing the rules is a few microseconds.** Even the worst case — a fact that trips all five AML
  typologies (large-amount, structuring, velocity, impossible-travel, new-payee-drain) — evaluates in
  ~4.3 µs. That is ~230k scored transactions per second per core from the rule engine alone, so Drools is
  nowhere near the bottleneck; the Kafka Streams state-store lookups that build the `ScoringFact` dominate
  real end-to-end latency, not the rule fire.
- **The RHS, not the pattern match, is the cost.** The clean fact (pattern-match only, no rule bodies run)
  is ~1.7× faster than the five-rule fact. The gap is the rules' right-hand sides — mostly building the
  human-readable reason strings that populate the alert. Matching itself is essentially free here.
- **Caching the `KieContainer` matters by ~5 orders of magnitude.** Compiling `aml.drl` into a fresh
  container costs ~1.83 **seconds** — roughly **425,000×** a single fire. `RiskScoringEngine` compiles it
  **once** at construction (`KieServices.getKieClasspathContainer()`) and only creates a cheap
  `StatelessKieSession` per evaluation. This benchmark is the receipt for that design choice: recompiling
  per transaction would collapse throughput from ~230k/s to well under 1/s.

### 2. Virtual threads win on blocking work — proportionally to how long you block

Each invocation is one burst of 500 concurrent "requests", every one blocking for `blockMillis`
(standing in for a slow DB / gRPC / HTTP downstream). The platform pool is a fixed **50** OS threads.

| Block per request | Platform pool (50 threads) | Virtual threads | Speed-up |
|---:|---:|---:|---:|
| 1 ms | 51.5 ops/s | 64.0 ops/s | ~1.2× |
| 20 ms | 3.2 ops/s | 32.1 ops/s | **~10×** |

- **At ~1 ms blocks the two are almost even.** A 50-thread pool already drains 500 near-instant tasks in a
  handful of waves, and spawning 500 virtual threads per burst carries its own (small) overhead — so the
  pool ceiling isn't the bottleneck yet.
- **As the block grows, pool serialization dominates and virtual threads pull ~10× ahead.** With a 20 ms
  block the bounded pool must serialize 500 tasks into `ceil(500/50) = 10` waves ≈ 200 ms+ per burst, while
  the virtual-thread executor runs all 500 concurrently and finishes a burst in ~one block-time. A blocking
  `Thread.sleep` *unmounts* the virtual thread (it does not pin the carrier), which is exactly why the
  model scales: throughput becomes bound by downstream latency, not by a thread-pool size.
- **This is the payoff of `spring.threads.virtual.enabled=true`.** Dolos' servlet handlers (transaction,
  alert, case, …) mostly block on JDBC / gRPC / Kafka. Under virtual threads their concurrency ceiling is
  the downstream, not a Tomcat worker count — no pool tuning, no reactive rewrite.

> **Windows caveat:** `Thread.sleep` granularity on Windows is coarse (often ~15 ms), so the absolute
> ops/s are lower and noisier than on Linux — but both executors pay the same tax, so the *ratio* (the
> point of the comparison) holds. The `blockMillis` sweep, not any single row, is the result.
