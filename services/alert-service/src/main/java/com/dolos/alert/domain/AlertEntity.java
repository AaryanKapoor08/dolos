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
 * <p>{@code transactionId} is unique: one alert per scored transaction, which is what makes alert
 * creation idempotent under redelivery. Never exposed over the API — mapped to a DTO first.
 */
@Entity
@Table(name = "alerts")
public class AlertEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, unique = true, updatable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(nullable = false)
    private int score;

    /** Reasons carried from scoring, stored newline-delimited in a single TEXT column. */
    @Convert(converter = AlertReasonsConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private List<String> reasons;

    @Column(name = "raised_at", nullable = false, updatable = false)
    private Instant raisedAt;

    /** Required by JPA. */
    protected AlertEntity() {}

    public AlertEntity(
            UUID id,
            UUID transactionId,
            String accountId,
            int score,
            List<String> reasons,
            Instant raisedAt) {
        this.id = id;
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.score = score;
        this.reasons = reasons;
        this.raisedAt = raisedAt;
    }

    public UUID getId() {
        return id;
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

    public Instant getRaisedAt() {
        return raisedAt;
    }
}
