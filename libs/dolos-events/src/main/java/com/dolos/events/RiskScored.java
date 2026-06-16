package com.dolos.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The risk assessment of a single transaction, published by scoring-service to
 * {@link Topics#RISK_SCORED} and consumed by alert-service.
 *
 * @param transactionId the scored transaction
 * @param accountId     the subject account (carried through so downstream need not re-look-up)
 * @param score         risk score, 0–100 (higher = riskier)
 * @param reasons       human-readable reasons the score was assigned (never {@code null}; may be empty)
 * @param scoredAt      when scoring happened
 */
public record RiskScored(
        UUID transactionId,
        String accountId,
        int score,
        List<String> reasons,
        Instant scoredAt) {

    public RiskScored {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        // Defensive copy + null-safety so the contract always carries an immutable, non-null list.
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        Objects.requireNonNull(scoredAt, "scoredAt must not be null");
    }
}
