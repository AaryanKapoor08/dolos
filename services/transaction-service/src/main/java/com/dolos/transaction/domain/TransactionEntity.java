package com.dolos.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code transactions} table. The schema is owned by the Flyway migration
 * {@code V1__transactions.sql}; this entity must stay in sync with it.
 *
 * <p>Never exposed over the API — controllers map to/from DTOs (see {@code api} package).
 */
@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(name = "counterparty_account_id", length = 64)
    private String counterpartyAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Direction direction;

    @Column(length = 255)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected TransactionEntity() {}

    public TransactionEntity(
            UUID id,
            String accountId,
            String counterpartyAccountId,
            BigDecimal amount,
            String currency,
            Direction direction,
            String description,
            Instant occurredAt,
            Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.counterpartyAccountId = counterpartyAccountId;
        this.amount = amount;
        this.currency = currency;
        this.direction = direction;
        this.description = description;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCounterpartyAccountId() {
        return counterpartyAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getDescription() {
        return description;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
