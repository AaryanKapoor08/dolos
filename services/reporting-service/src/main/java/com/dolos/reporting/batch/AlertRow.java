package com.dolos.reporting.batch;

import java.time.Instant;
import java.util.UUID;

/**
 * One row read from the alert read model ({@code alert.alert_view}) — the batch reader's input item.
 * {@code transactionId} is null for ring alerts (which are account-level, not tied to one transaction).
 */
public record AlertRow(
        UUID alertId,
        UUID transactionId,
        String accountId,
        int score,
        String severity,
        String title,
        String reasons,
        String detail,
        Instant raisedAt) {}
