package com.dolos.alert.api.dto;

import com.dolos.common.AccountId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbound representation of an alert. Uses the shared {@link AccountId} value object from
 * dolos-common so the API never leaks the JPA entity.
 *
 * @param alertId       stable id of the alert
 * @param transactionId the transaction that triggered it
 * @param account       the subject account
 * @param score         the risk score that crossed the threshold
 * @param reasons       human-readable reasons carried from scoring
 * @param detail        score detail fetched from scoring-service over gRPC (or a fallback)
 * @param raisedAt      when the alert was raised
 */
public record AlertResponse(
        UUID alertId,
        UUID transactionId,
        AccountId account,
        int score,
        List<String> reasons,
        String detail,
        Instant raisedAt) {}
