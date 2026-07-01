package com.dolos.bench;

import com.dolos.scoring.service.RiskScoringEngine;
import com.dolos.scoring.service.ScoringFact;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * 6F benchmark #1 — the Drools rule-session hot path (scoring-service).
 *
 * <p>This exercises the REAL engine ({@link RiskScoringEngine}) firing the REAL AML typologies
 * (scoring-service's {@code rules/aml.drl}, compiled from the classpath into a {@link KieContainer}).
 * Nothing here re-implements the rules — the point is to measure the steady-state cost the production
 * pipeline actually pays per transaction.
 *
 * <p>Three measurements:
 * <ul>
 *   <li>{@code scoreHighRiskFact} — a fact that trips five typologies (large-amount, structuring,
 *       velocity, impossible-travel, new-payee-drain). The worst-case pattern-matching + RHS cost.</li>
 *   <li>{@code scoreCleanFact} — a fact that trips nothing. The floor: pattern matching with no RHS.</li>
 *   <li>{@code buildFreshKieContainer} — compiling the DRL into a fresh container. This is the ONE-TIME
 *       cost the engine amortizes by caching the classpath container at construction; measuring it shows
 *       why per-eval recompilation would be catastrophic (it is orders of magnitude slower than a fire).</li>
 * </ul>
 */
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class DroolsScoringBenchmark {

    private RiskScoringEngine engine;
    private ScoringFact highRiskFact;
    private ScoringFact cleanFact;

    @Setup
    public void setup() {
        // Same construction path as the running service: compiles rules/aml.drl once via the cached
        // classpath KieContainer.
        engine = new RiskScoringEngine();

        long occurredAtMs = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

        // Trips large-amount (>=10k), structuring (>=3 sub-10k summing >=9k), velocity (>=5),
        // impossible-travel (country change within 2h), and new-payee-drain (new payee DEBIT >=5k).
        highRiskFact = new ScoringFact(
                UUID.randomUUID(),
                "ACC-HIGH",
                new BigDecimal("15000"),
                "RU",
                occurredAtMs,
                "PAYEE-NEW",
                "DEBIT",
                6,
                new BigDecimal("42000"),
                4,
                new BigDecimal("9500"),
                "GB",
                occurredAtMs - 3_600_000L, // 1h earlier, different country -> impossible travel
                true);

        // Trips nothing: small credit, no windowed history.
        cleanFact = new ScoringFact(
                UUID.randomUUID(),
                "ACC-CLEAN",
                new BigDecimal("100"),
                "US",
                occurredAtMs,
                "PAYEE-KNOWN",
                "CREDIT",
                1,
                new BigDecimal("100"),
                0,
                BigDecimal.ZERO,
                null,
                null,
                false);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Object scoreHighRiskFact() {
        return engine.score(highRiskFact);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Object scoreCleanFact() {
        return engine.score(cleanFact);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public KieContainer buildFreshKieContainer() {
        // Deliberately bypasses the cache to measure DRL compilation cost per call.
        return KieServices.get().newKieClasspathContainer();
    }
}
