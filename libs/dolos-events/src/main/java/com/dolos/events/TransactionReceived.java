package com.dolos.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A transaction observed at the edge (ingestion-service) and published to
 * {@link Topics#TRANSACTIONS_RECEIVED}. This is the entry point of the event backbone:
 * transaction-service persists it, scoring-service scores it.
 *
 * <p>{@code direction} is carried as a plain String ("DEBIT"/"CREDIT") to keep the wire
 * contract free of any service-owned enum; consumers map it to their own domain type.
 *
 * @param transactionId        stable id assigned at ingestion (used for idempotent persistence)
 * @param accountId            the subject account
 * @param counterpartyAccountId the other side of the transfer, may be {@code null}
 * @param amount               transaction amount (positive)
 * @param currency             ISO-4217 currency code
 * @param direction            "DEBIT" (money leaves) or "CREDIT" (money enters) the subject account
 * @param description          free-text description, may be {@code null}
 * @param country              ISO-3166 alpha-2 country the transaction originated in, may be
 *                             {@code null}. Carried so stateful scoring (Phase 2A) can track an
 *                             account's last-seen location for the impossible-travel typology.
 * @param occurredAt           when the transaction took place
 * @param receivedAt           when ingestion observed it
 */
public record TransactionReceived(
        UUID transactionId,
        String accountId,
        String counterpartyAccountId,
        BigDecimal amount,
        String currency,
        String direction,
        String description,
        String country,
        Instant occurredAt,
        Instant receivedAt) {

    public TransactionReceived {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    }
}
