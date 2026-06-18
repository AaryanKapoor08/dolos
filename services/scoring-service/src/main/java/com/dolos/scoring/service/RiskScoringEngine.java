package com.dolos.scoring.service;

import com.dolos.events.RiskScored;
import com.dolos.events.TransactionReceived;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The v0 risk rule (Phase 1D): a single-transaction, in-code heuristic. It can only see one
 * transaction at a time — no velocity, no per-customer daily totals, no travel — which is exactly
 * the limitation Phase 2 removes with a stateful Kafka Streams topology and a Drools rule set.
 *
 * <p>Scores are 0–100 (higher = riskier). Every transaction is scored and published (even a 0) so
 * the decision of what crosses the alert threshold lives in alert-service, not here.
 */
@Component
public class RiskScoringEngine {

    /** The classic AML cash reporting threshold; at/over it is independently noteworthy. */
    static final BigDecimal REPORTING_THRESHOLD = new BigDecimal("10000");

    /** Amounts in [floor, threshold) look like deliberate "structuring" just under the line. */
    static final BigDecimal STRUCTURING_FLOOR = new BigDecimal("9000");

    private static final int SCORE_LARGE_AMOUNT = 60;
    private static final int SCORE_POSSIBLE_STRUCTURING = 50;
    private static final int MAX_SCORE = 100;

    /** Scores one transaction into a {@link RiskScored} event. */
    public RiskScored score(TransactionReceived txn) {
        List<String> reasons = new ArrayList<>();
        int score = 0;

        BigDecimal amount = txn.amount();
        if (amount.compareTo(REPORTING_THRESHOLD) >= 0) {
            score += SCORE_LARGE_AMOUNT;
            reasons.add(
                    "LARGE_AMOUNT: amount " + amount + " is at/above the $10k reporting threshold");
        } else if (amount.compareTo(STRUCTURING_FLOOR) >= 0) {
            score += SCORE_POSSIBLE_STRUCTURING;
            reasons.add(
                    "POSSIBLE_STRUCTURING: amount " + amount + " sits just under the $10k threshold");
        }

        score = Math.min(score, MAX_SCORE);
        return new RiskScored(txn.transactionId(), txn.accountId(), score, reasons, Instant.now());
    }
}
