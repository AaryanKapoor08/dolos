package com.dolos.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An alert raised by alert-service when a {@link RiskScored} crosses the alert threshold,
 * published to {@link Topics#ALERTS_RAISED}. Consumed by later phases (case-service,
 * notifications); for now it makes the end-of-pipeline outcome observable.
 *
 * @param alertId       stable id of the alert
 * @param transactionId the transaction that triggered it
 * @param accountId     the subject account
 * @param score         the risk score that crossed the threshold
 * @param reasons       the reasons carried from scoring (never {@code null}; may be empty)
 * @param raisedAt      when the alert was raised
 */
public record AlertRaised(
        UUID alertId,
        UUID transactionId,
        String accountId,
        int score,
        List<String> reasons,
        Instant raisedAt) {

    public AlertRaised {
        Objects.requireNonNull(alertId, "alertId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        Objects.requireNonNull(raisedAt, "raisedAt must not be null");
    }
}
