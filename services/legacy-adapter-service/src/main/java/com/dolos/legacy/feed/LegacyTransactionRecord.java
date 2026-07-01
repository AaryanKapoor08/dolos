package com.dolos.legacy.feed;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One decoded record from the legacy partner's fixed-width feed — the intermediate form between the raw
 * fixed-width line and the canonical {@code TransactionReceived} event. Fields already validated +
 * normalized by {@link LegacyFeedParser}: {@code amount} is in major units, {@code direction} is the
 * canonical {@code "DEBIT"}/{@code "CREDIT"} (not the wire's {@code D}/{@code C}), and blank optional
 * columns are {@code null}.
 *
 * @param recordType             the record-type marker (always {@code "TX"} for a transaction row)
 * @param partnerRef             the partner's own transaction reference (kept for traceability)
 * @param accountId              the subject account
 * @param counterpartyAccountId  the other side of the transfer, {@code null} if the column was blank
 * @param amount                 transaction amount in major units (decoded from minor units)
 * @param currency               ISO-4217 currency code
 * @param direction              {@code "DEBIT"} or {@code "CREDIT"}
 * @param country                ISO-3166 alpha-2 country, {@code null} if the column was blank
 * @param occurredAt             when the transaction took place
 * @param description            free-text description, {@code null} if the column was blank
 */
public record LegacyTransactionRecord(
        String recordType,
        String partnerRef,
        String accountId,
        String counterpartyAccountId,
        BigDecimal amount,
        String currency,
        String direction,
        String country,
        Instant occurredAt,
        String description) {}
