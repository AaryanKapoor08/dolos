package com.dolos.alert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CQRS <b>read model</b> for the analyst alert queue (Phase 2F), mapping the {@code alert_view} table.
 *
 * <p>A denormalized projection of {@link AlertEntity} (the write model / source of truth): it carries
 * a precomputed {@code severity} bucket and a one-line {@code title} so the queue renders with no
 * joins or post-processing, and is indexed for the default risk-sorted scan. Keyed by the same
 * {@code alertId} as the write row, and upserted by {@code AlertProjector}, so re-projecting an alert
 * is idempotent. Owns no truth — rebuildable from the write model at any time.
 */
@Entity
@Table(name = "alert_view")
public class AlertView {

    /** Risk bucket precomputed from the score, so the UI doesn't re-derive it per render. */
    public enum Severity {
        HIGH,
        MEDIUM,
        LOW
    }

    @Id
    @Column(name = "alert_id", nullable = false, updatable = false)
    private UUID alertId;

    @Column(name = "dedupe_key", nullable = false, unique = true)
    private String dedupeKey;

    @Column(name = "alert_type", nullable = false, length = 16)
    private String alertType;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, length = 8)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Severity severity;

    @Column(nullable = false, length = 256)
    private String title;

    @Convert(converter = AlertReasonsConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private List<String> reasons;

    @Column(name = "detail")
    private String detail;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    /** Required by JPA. */
    protected AlertView() {}

    public AlertView(
            UUID alertId,
            String dedupeKey,
            String alertType,
            UUID transactionId,
            String accountId,
            int score,
            Severity severity,
            String title,
            List<String> reasons,
            String detail,
            Instant raisedAt) {
        this.alertId = alertId;
        this.dedupeKey = dedupeKey;
        this.alertType = alertType;
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.score = score;
        this.severity = severity;
        this.title = title;
        this.reasons = reasons;
        this.detail = detail;
        this.raisedAt = raisedAt;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public String getAlertType() {
        return alertType;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public int getScore() {
        return score;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }
}
