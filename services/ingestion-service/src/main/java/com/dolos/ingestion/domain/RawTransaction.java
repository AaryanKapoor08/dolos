package com.dolos.ingestion.domain;

import com.dolos.ingestion.api.dto.IngestTransactionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC mapping for the {@code raw_transactions} audit table. The schema is owned by the Flyway
 * migration {@code V1__raw_transactions.sql}; this class must stay in sync with it.
 *
 * <p>Always inserted (never updated) via {@code R2dbcEntityTemplate.insert}, so we don't rely on
 * Spring Data's new-vs-existing entity detection. Column names are mapped explicitly because the
 * default relational naming strategy does not convert camelCase to snake_case.
 */
@Table("raw_transactions")
public class RawTransaction {

    @Id
    private final UUID id;

    @Column("account_id")
    private final String accountId;

    @Column("counterparty_account_id")
    private final String counterpartyAccountId;

    @Column("amount")
    private final BigDecimal amount;

    @Column("currency")
    private final String currency;

    @Column("direction")
    private final String direction;

    @Column("description")
    private final String description;

    @Column("country")
    private final String country;

    @Column("customer_id")
    private final String customerId;

    @Column("device_id")
    private final String deviceId;

    @Column("occurred_at")
    private final Instant occurredAt;

    @Column("received_at")
    private final Instant receivedAt;

    @Column("status")
    private final String status;

    public RawTransaction(
            UUID id,
            String accountId,
            String counterpartyAccountId,
            BigDecimal amount,
            String currency,
            String direction,
            String description,
            String country,
            String customerId,
            String deviceId,
            Instant occurredAt,
            Instant receivedAt,
            String status) {
        this.id = id;
        this.accountId = accountId;
        this.counterpartyAccountId = counterpartyAccountId;
        this.amount = amount;
        this.currency = currency;
        this.direction = direction;
        this.description = description;
        this.country = country;
        this.customerId = customerId;
        this.deviceId = deviceId;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.status = status;
    }

    /** Builds a freshly-received raw record from an inbound request. */
    public static RawTransaction received(UUID id, IngestTransactionRequest req, Instant receivedAt) {
        return new RawTransaction(
                id,
                req.accountId(),
                req.counterpartyAccountId(),
                req.amount(),
                req.currency(),
                req.direction(),
                req.description(),
                req.country(),
                req.customerId(),
                req.deviceId(),
                req.occurredAt(),
                receivedAt,
                "RECEIVED");
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

    public String getDirection() {
        return direction;
    }

    public String getDescription() {
        return description;
    }

    public String getCountry() {
        return country;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getStatus() {
        return status;
    }
}
