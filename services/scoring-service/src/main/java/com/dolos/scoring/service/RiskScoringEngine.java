package com.dolos.scoring.service;

import com.dolos.events.RiskScored;
import com.dolos.events.TransactionReceived;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The stateful risk rule set (Phase 2A). Unlike the v0 engine, it scores a {@link ScoringFact} —
 * the transaction <em>plus</em> the windowed aggregates the Kafka Streams topology computed — so it
 * can catch patterns no single-transaction rule could: a burst of sub-$10k deposits that <em>sum</em>
 * past the reporting line (structuring), a spike in transaction count/value (velocity), and a country
 * change in an implausibly short time (impossible travel).
 *
 * <p>Scores are 0–100 (higher = riskier). Every transaction is scored and published (even a 0) so the
 * alert-threshold decision stays in alert-service. Phase 2B moves these typologies into Drools DRL so
 * they become editable without recompiling; this engine is the bridge to that.
 */
@Component
public class RiskScoringEngine {

    /** The classic AML cash reporting threshold; a single transaction at/over it is noteworthy. */
    public static final BigDecimal REPORTING_THRESHOLD = new BigDecimal("10000");

    /** Structuring window: deposits that sum near/over the threshold within a day look deliberate. */
    public static final Duration STRUCTURING_WINDOW = Duration.ofDays(1);

    /** At least this many sub-threshold deposits in the window before structuring is considered. */
    static final int STRUCTURING_MIN_DEPOSITS = 3;

    /** ...and they must sum to at least this for the pattern to fire (just under the $10k line). */
    static final BigDecimal STRUCTURING_MIN_SUM = new BigDecimal("9000");

    /** Velocity window: a flurry of transactions in a short window is itself suspicious. */
    public static final Duration VELOCITY_WINDOW = Duration.ofHours(1);

    /** At least this many transactions on one account inside the velocity window fires velocity. */
    static final int VELOCITY_MIN_COUNT = 5;

    /** A country change within this gap is implausibly fast travel. */
    static final Duration IMPOSSIBLE_TRAVEL_GAP = Duration.ofHours(2);

    private static final int SCORE_LARGE_AMOUNT = 60;
    private static final int SCORE_STRUCTURING = 70;
    private static final int SCORE_VELOCITY = 40;
    private static final int SCORE_IMPOSSIBLE_TRAVEL = 50;
    private static final int MAX_SCORE = 100;

    /** Scores one {@link ScoringFact} into a {@link RiskScored} event. */
    public RiskScored score(ScoringFact fact) {
        TransactionReceived txn = fact.transaction();
        List<String> reasons = new ArrayList<>();
        int score = 0;

        BigDecimal amount = txn.amount();
        if (amount.compareTo(REPORTING_THRESHOLD) >= 0) {
            score += SCORE_LARGE_AMOUNT;
            reasons.add("LARGE_AMOUNT: amount " + amount + " is at/above the $10k reporting threshold");
        }

        if (fact.structuringCount() >= STRUCTURING_MIN_DEPOSITS
                && fact.structuringSum().compareTo(STRUCTURING_MIN_SUM) >= 0) {
            score += SCORE_STRUCTURING;
            reasons.add(
                    "STRUCTURING: "
                            + fact.structuringCount()
                            + " sub-$10k deposits summing to "
                            + fact.structuringSum()
                            + " within 24h on account "
                            + txn.accountId());
        }

        if (fact.velocityCount() >= VELOCITY_MIN_COUNT) {
            score += SCORE_VELOCITY;
            reasons.add(
                    "VELOCITY: "
                            + fact.velocityCount()
                            + " transactions totalling "
                            + fact.velocitySum()
                            + " within 1h on account "
                            + txn.accountId());
        }

        if (isImpossibleTravel(fact)) {
            score += SCORE_IMPOSSIBLE_TRAVEL;
            reasons.add(
                    "IMPOSSIBLE_TRAVEL: account "
                            + txn.accountId()
                            + " moved from "
                            + fact.priorCountry()
                            + " to "
                            + txn.country()
                            + " in under "
                            + IMPOSSIBLE_TRAVEL_GAP.toHours()
                            + "h");
        }

        score = Math.min(score, MAX_SCORE);
        return new RiskScored(txn.transactionId(), txn.accountId(), score, reasons, Instant.now());
    }

    private static boolean isImpossibleTravel(ScoringFact fact) {
        String priorCountry = fact.priorCountry();
        Long priorMs = fact.priorOccurredAtMs();
        String country = fact.transaction().country();
        if (priorCountry == null || priorMs == null || country == null) {
            return false;
        }
        if (priorCountry.equals(country)) {
            return false;
        }
        long gapMs = fact.transaction().occurredAt().toEpochMilli() - priorMs;
        return gapMs >= 0 && gapMs < IMPOSSIBLE_TRAVEL_GAP.toMillis();
    }
}
