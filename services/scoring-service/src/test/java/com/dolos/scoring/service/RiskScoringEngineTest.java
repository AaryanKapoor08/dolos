package com.dolos.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dolos.events.RiskScored;
import com.dolos.events.TransactionReceived;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskScoringEngineTest {

    private final RiskScoringEngine engine = new RiskScoringEngine();

    private static TransactionReceived txn(String amount) {
        return new TransactionReceived(
                UUID.randomUUID(),
                "ACC-1",
                "ACC-2",
                new BigDecimal(amount),
                "CAD",
                "DEBIT",
                "test",
                Instant.parse("2026-02-02T00:00:00Z"),
                Instant.parse("2026-02-02T00:00:01Z"));
    }

    @Test
    void smallAmount_scoresZeroWithNoReasons() {
        RiskScored scored = engine.score(txn("100.00"));

        assertThat(scored.score()).isZero();
        assertThat(scored.reasons()).isEmpty();
        assertThat(scored.transactionId()).isNotNull();
        assertThat(scored.accountId()).isEqualTo("ACC-1");
    }

    @Test
    void atOrAboveReportingThreshold_flagsLargeAmount() {
        RiskScored scored = engine.score(txn("10000.00"));

        assertThat(scored.score()).isEqualTo(60);
        assertThat(scored.reasons()).hasSize(1).first().asString().contains("LARGE_AMOUNT");
    }

    @Test
    void justUnderThreshold_flagsPossibleStructuring() {
        RiskScored scored = engine.score(txn("9500.00"));

        assertThat(scored.score()).isEqualTo(50);
        assertThat(scored.reasons()).hasSize(1).first().asString().contains("POSSIBLE_STRUCTURING");
    }

    @Test
    void belowStructuringFloor_isNotFlagged() {
        RiskScored scored = engine.score(txn("8999.99"));

        assertThat(scored.score()).isZero();
        assertThat(scored.reasons()).isEmpty();
    }
}
