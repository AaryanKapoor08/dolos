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
 * JPA entity mapping the {@code alerts} table. The schema is owned by the Flyway migration
 * {@code V1__alerts.sql} (in alert-service's own {@code alert} schema); this entity must stay in
 * sync with it.
 *
 * <p>{@code dedupeKey} is the unified idempotency key (the transaction id for a scored-transaction
 * alert, the ringId for a graph-ring alert): unique, so a redelivered event never double-alerts.
 * {@code transactionId} is null for ring alerts. Never exposed over the API — mapped to a DTO first.
 */
@Entity
@Table(name = "alerts")
public class AlertEntity {

    /** What triggered the alert: a scored transaction, or a detected graph ring (Phase 2E). */
    public enum Type {
        TRANSACTION,
        RING
    }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Unified idempotency key: transaction id (TRANSACTION) or ringId (RING). */
    @Column(name = "dedupe_key", nullable = false, unique = true, updatable = false)
    private String dedupeKey;

    @Column(name = "alert_type", nullable = false, updatable = false, length = 16)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Type alertType;

    @Column(name = "transaction_id", unique = true, updatable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(nullable = false)
    private int score;

    /** Reasons carried from scoring, stored newline-delimited in a single TEXT column. */
    @Convert(converter = AlertReasonsConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private List<String> reasons;

    /** Score detail fetched synchronously from scoring-service over gRPC (Phase 2C); may be a fallback. */
    @Column(name = "detail")
    private String detail;

    @Column(name = "raised_at", nullable = false, updatable = false)
    private Instant raisedAt;

    /** Required by JPA. */
    protected AlertEntity() {}

    private AlertEntity(
            UUID id,
            String dedupeKey,
            Type alertType,
            UUID transactionId,
            String accountId,
            int score,
            List<String> reasons,
            String detail,
            Instant raisedAt) {
        this.id = id;
        this.dedupeKey = dedupeKey;
        this.alertType = alertType;
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.score = score;
        this.reasons = reasons;
        this.detail = detail;
        this.raisedAt = raisedAt;
    }

    /** An alert for a scored transaction (Phase 1E): idempotency key is the transaction id. */
    public static AlertEntity forTransaction(
            UUID id,
            UUID transactionId,
            String accountId,
            int score,
            List<String> reasons,
            String detail,
            Instant raisedAt) {
        return new AlertEntity(
                id, transactionId.toString(), Type.TRANSACTION, transactionId, accountId, score,
                reasons, detail, raisedAt);
    }

    /** An alert for a detected graph ring (Phase 2E): idempotency key is the ringId; no transaction. */
    public static AlertEntity forRing(
            UUID id,
            String ringId,
            String accountId,
            int score,
            List<String> reasons,
            String detail,
            Instant raisedAt) {
        return new AlertEntity(
                id, ringId, Type.RING, null, accountId, score, reasons, detail, raisedAt);
    }

    public UUID getId() {
        return id;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public Type getAlertType() {
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
