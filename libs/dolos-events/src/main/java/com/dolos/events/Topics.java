package com.dolos.events;

/**
 * Canonical Kafka topic names. Centralised here so producers and consumers cannot drift
 * on a string literal. Naming: dotted, {@code <noun>.<past-tense-event>}.
 */
public final class Topics {

    /** Raw inbound transactions, published by ingestion-service. */
    public static final String TRANSACTIONS_RECEIVED = "transactions.received";

    /** Risk scores produced by scoring-service for each received transaction. */
    public static final String RISK_SCORED = "risk.scored";

    /** Alerts raised by alert-service when a score crosses the threshold. */
    public static final String ALERTS_RAISED = "alerts.raised";

    /** Mule/cash-out rings detected by graph-service from the transaction graph (Phase 2E). */
    public static final String RINGS_DETECTED = "rings.detected";

    private Topics() {}
}
