package com.dolos.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dolos.events.RiskScored;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Per-typology tests for the Drools rule set (Phase 2B). Each test crafts a {@link ScoringFact} that
 * should trip exactly one rule and asserts the rule fired (reason + points), plus a quiet fact that
 * trips nothing. The engine compiles {@code rules/aml.drl} from the classpath, so these exercise the
 * real DRL — not a Java stand-in.
 */
class RiskScoringEngineTest {

    private static final long T0 = Instant.parse("2026-02-02T12:00:00Z").toEpochMilli();

    private final RiskScoringEngine engine = new RiskScoringEngine();

    @Test
    void largeAmount_fires() {
        RiskScored scored = engine.score(new FactBuilder().amount("10000").build());
        assertThat(scored.score()).isEqualTo(60);
        assertThat(scored.reasons()).anySatisfy(r -> assertThat(r).contains("LARGE_AMOUNT"));
    }

    @Test
    void structuring_fires() {
        RiskScored scored =
                engine.score(
                        new FactBuilder()
                                .amount("2400")
                                .structuring(4, "9600")
                                .build());
        assertThat(scored.score()).isEqualTo(70);
        assertThat(scored.reasons()).anySatisfy(r -> assertThat(r).contains("STRUCTURING"));
    }

    @Test
    void velocity_fires() {
        RiskScored scored =
                engine.score(new FactBuilder().amount("500").velocity(6, "3000").build());
        assertThat(scored.score()).isEqualTo(40);
        assertThat(scored.reasons()).anySatisfy(r -> assertThat(r).contains("VELOCITY"));
    }

    @Test
    void impossibleTravel_fires() {
        RiskScored scored =
                engine.score(
                        new FactBuilder()
                                .amount("500")
                                .country("GB")
                                .prior("CA", T0 - Duration.ofMinutes(30).toMillis())
                                .build());
        assertThat(scored.score()).isEqualTo(50);
        assertThat(scored.reasons()).anySatisfy(r -> assertThat(r).contains("IMPOSSIBLE_TRAVEL"));
    }

    @Test
    void dormantWake_fires() {
        RiskScored scored =
                engine.score(
                        new FactBuilder()
                                .amount("6000")
                                .prior("CA", T0 - Duration.ofDays(100).toMillis())
                                .build());
        assertThat(scored.score()).isEqualTo(45);
        assertThat(scored.reasons()).anySatisfy(r -> assertThat(r).contains("DORMANT_WAKE"));
    }

    @Test
    void newPayeeDrain_fires() {
        RiskScored scored =
                engine.score(
                        new FactBuilder()
                                .amount("6000")
                                .direction("DEBIT")
                                .counterparty("ACC-NEW")
                                .newPayee(true)
                                .build());
        assertThat(scored.score()).isEqualTo(45);
        assertThat(scored.reasons()).anySatisfy(r -> assertThat(r).contains("NEW_PAYEE_DRAIN"));
    }

    @Test
    void quietFact_firesNothing() {
        RiskScored scored = engine.score(new FactBuilder().amount("100").build());
        assertThat(scored.score()).isZero();
        assertThat(scored.reasons()).isEmpty();
    }

    /** Fluent builder with unremarkable defaults; each test overrides only what its rule needs. */
    private static final class FactBuilder {
        private BigDecimal amount = new BigDecimal("100");
        private String country = "CA";
        private final long occurredAtMs = T0;
        private String counterparty = null;
        private String direction = "CREDIT";
        private int velocityCount = 1;
        private BigDecimal velocitySum = new BigDecimal("100");
        private int structuringCount = 0;
        private BigDecimal structuringSum = BigDecimal.ZERO;
        private String priorCountry = null;
        private Long priorOccurredAtMs = null;
        private boolean newPayee = false;

        FactBuilder amount(String a) {
            this.amount = new BigDecimal(a);
            return this;
        }

        FactBuilder country(String c) {
            this.country = c;
            return this;
        }

        FactBuilder direction(String d) {
            this.direction = d;
            return this;
        }

        FactBuilder counterparty(String cp) {
            this.counterparty = cp;
            return this;
        }

        FactBuilder velocity(int count, String sum) {
            this.velocityCount = count;
            this.velocitySum = new BigDecimal(sum);
            return this;
        }

        FactBuilder structuring(int count, String sum) {
            this.structuringCount = count;
            this.structuringSum = new BigDecimal(sum);
            return this;
        }

        FactBuilder prior(String country, long occurredAtMs) {
            this.priorCountry = country;
            this.priorOccurredAtMs = occurredAtMs;
            return this;
        }

        FactBuilder newPayee(boolean v) {
            this.newPayee = v;
            return this;
        }

        ScoringFact build() {
            return new ScoringFact(
                    UUID.randomUUID(),
                    "ACC-1",
                    amount,
                    country,
                    occurredAtMs,
                    counterparty,
                    direction,
                    velocityCount,
                    velocitySum,
                    structuringCount,
                    structuringSum,
                    priorCountry,
                    priorOccurredAtMs,
                    newPayee);
        }
    }
}
