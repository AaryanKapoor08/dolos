package com.dolos.scoring.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The fact the AML rules score (Phase 2B): the transaction under assessment, flattened together with
 * the windowed state the Kafka Streams topology computed for it. This is what lifts scoring out of
 * the v0 single-transaction blindness — the rules see recent history, not just one row.
 *
 * <p>Deliberately a plain JavaBean (not a record) with {@code getX()} accessors: Drools' pattern
 * matching binds on bean properties, and fields are flattened (amount, country, time on the fact
 * itself rather than nested) so the DRL reads as clean domain conditions. Built by the streams
 * adapter from the state stores and handed to the {@link RiskScoringEngine}'s Drools session.
 */
public final class ScoringFact {

    private final UUID transactionId;
    private final String accountId;
    private final BigDecimal amount;
    private final String country;
    private final long occurredAtMs;
    private final String counterparty;
    private final String direction;
    private final int velocityCount;
    private final BigDecimal velocitySum;
    private final int structuringCount;
    private final BigDecimal structuringSum;
    private final String priorCountry;
    private final Long priorOccurredAtMs;
    private final boolean newPayee;

    public ScoringFact(
            UUID transactionId,
            String accountId,
            BigDecimal amount,
            String country,
            long occurredAtMs,
            String counterparty,
            String direction,
            int velocityCount,
            BigDecimal velocitySum,
            int structuringCount,
            BigDecimal structuringSum,
            String priorCountry,
            Long priorOccurredAtMs,
            boolean newPayee) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount;
        this.country = country;
        this.occurredAtMs = occurredAtMs;
        this.counterparty = counterparty;
        this.direction = direction;
        this.velocityCount = velocityCount;
        this.velocitySum = velocitySum;
        this.structuringCount = structuringCount;
        this.structuringSum = structuringSum;
        this.priorCountry = priorCountry;
        this.priorOccurredAtMs = priorOccurredAtMs;
        this.newPayee = newPayee;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCountry() {
        return country;
    }

    public long getOccurredAtMs() {
        return occurredAtMs;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public String getDirection() {
        return direction;
    }

    public int getVelocityCount() {
        return velocityCount;
    }

    public BigDecimal getVelocitySum() {
        return velocitySum;
    }

    public int getStructuringCount() {
        return structuringCount;
    }

    public BigDecimal getStructuringSum() {
        return structuringSum;
    }

    public String getPriorCountry() {
        return priorCountry;
    }

    public Long getPriorOccurredAtMs() {
        return priorOccurredAtMs;
    }

    public boolean isNewPayee() {
        return newPayee;
    }
}
