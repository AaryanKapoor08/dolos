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

    // Case lifecycle events, published by case-service via the Spring Modulith outbox (Phase 3E).
    // One type per topic (the wire is header-less JSON, so a topic carries a single event type).

    /** A case was opened (from a HIGH alert or by an analyst). */
    public static final String CASES_OPENED = "cases.opened";

    /** A case was escalated to a senior analyst. */
    public static final String CASES_ESCALATED = "cases.escalated";

    /** A SAR/STR report was filed for a case. */
    public static final String CASES_REPORTED = "cases.reported";

    /** A case was closed (terminal). */
    public static final String CASES_CLOSED = "cases.closed";

    private Topics() {}
}
