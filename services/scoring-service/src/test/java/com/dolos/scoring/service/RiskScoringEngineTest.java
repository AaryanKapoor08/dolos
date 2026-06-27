package com.dolos.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dolos.events.RiskScored;
import com.dolos.events.TransactionReceived;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the stateful engine (Phase 2A). The engine is fed {@link ScoringFact}s directly —
 * no Kafka, no stores — proving each typology fires off the aggregates alone. The end-to-end wiring
 * (stores producing those aggregates) is covered by the {@code TopologyTestDriver} test.
 */
class RiskScoringEngineTest {

    private static final Instant T0 = Instant.parse("2026-02-02T12:00:00Z");

    private final RiskScoringEngine engine = new RiskScoringEngine();

    private static TransactionReceived txn(String amount, String country, Instant occurredAt) {
        return new TransactionReceived(
                UUID.randomUUID(),
                "ACC-1",
                "ACC-2",
                new BigDecimal(amount),
                "CAD",
                "DEBIT",
                "test",
                country,
                occurredAt,
                occurredAt.plusSeconds(1));
    }

    /** A fact with no notable history — just the one transaction. */
    private static ScoringFact lone(String amount) {
        return new ScoringFact(
                txn(amount, "CA", T0), 1, new BigDecimal(amount), 0, BigDecimal.ZERO, null, null);
    }

    @Test
    void smallLoneAmount_scoresZeroWithNoReasons() {
        RiskScored scored = engine.score(lone("100.00"));

        assertThat(scored.score()).isZero();
        assertThat(scored.reasons()).isEmpty();
        assertThat(scored.accountId()).isEqualTo("ACC-1");
    }

    @Test
    void atOrAboveReportingThreshold_flagsLargeAmount() {
        RiskScored scored = engine.score(lone("10000.00"));

        assertThat(scored.score()).isEqualTo(60);
        assertThat(scored.reasons()).hasSize(1).first().asString().contains("LARGE_AMOUNT");
    }

    @Test
    void burstOfSubThresholdDeposits_flagsStructuring() {
        // 4 sub-$10k deposits summing to 9600 within the day — the v0 engine could never see this.
        ScoringFact fact =
                new ScoringFact(
                        txn("2400.00", "CA", T0), 4, new BigDecimal("9600.00"),
                        4, new BigDecimal("9600.00"), null, null);

        RiskScored scored = engine.score(fact);

        assertThat(scored.score()).isEqualTo(70);
        assertThat(scored.reasons()).anySatisfy(r -> assertThat(r).contains("STRUCTURING"));
    }

    @Test
    void fewSubThresholdDeposits_doesNotFlagStructuring() {
        ScoringFact fact =
                new ScoringFact(
                        txn("2400.00", "CA", T0), 2, new BigDecimal("4800.00"),
                        2, new BigDecimal("4800.00"), null, null);

        RiskScored scored = engine.score(fact);

        assertThat(scored.score()).isZero();
        assertThat(scored.reasons()).isEmpty();
    }

    @Test
    void manyTransactionsInWindow_flagsVelocity() {
        ScoringFact fact =
                new ScoringFact(
                        txn("500.00", "CA", T0), 6, new BigDecimal("3000.00"),
                        0, BigDecimal.ZERO, null, null);

        RiskScored scored = engine.score(fact);

        assertThat(scored.score()).isEqualTo(40);
        assertThat(scored.reasons()).anySatisfy(r -> assertThat(r).contains("VELOCITY"));
    }

    @Test
    void countryChangeInShortGap_flagsImpossibleTravel() {
        // Prior txn in CA 30 minutes ago; this one in GB — implausibly fast.
        long priorMs = T0.minusSeconds(1800).toEpochMilli();
        ScoringFact fact =
                new ScoringFact(
                        txn("500.00", "GB", T0), 1, new BigDecimal("500.00"),
                        0, BigDecimal.ZERO, "CA", priorMs);

        RiskScored scored = engine.score(fact);

        assertThat(scored.score()).isEqualTo(50);
        assertThat(scored.reasons()).anySatisfy(r -> assertThat(r).contains("IMPOSSIBLE_TRAVEL"));
    }

    @Test
    void sameCountry_doesNotFlagTravel() {
        long priorMs = T0.minusSeconds(60).toEpochMilli();
        ScoringFact fact =
                new ScoringFact(
                        txn("500.00", "CA", T0), 1, new BigDecimal("500.00"),
                        0, BigDecimal.ZERO, "CA", priorMs);

        RiskScored scored = engine.score(fact);

        assertThat(scored.score()).isZero();
    }
}
