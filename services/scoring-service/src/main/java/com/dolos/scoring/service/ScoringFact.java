package com.dolos.scoring.service;

import com.dolos.events.TransactionReceived;
import java.math.BigDecimal;

/**
 * The unit of work the {@link RiskScoringEngine} scores: the transaction under assessment plus the
 * windowed state the Kafka Streams topology computed for it. This is what lifts scoring out of the
 * v0 single-transaction blindness — the engine now sees recent history, not just one row.
 *
 * <p>Deliberately transport-agnostic (a plain record in the domain {@code service} package): the
 * streams adapter builds it from state stores, and Phase 2B will hand it to a Drools session. The
 * engine never learns where the facts came from.
 *
 * @param transaction        the transaction being scored
 * @param velocityCount      number of transactions on this account within the velocity window (incl. this one)
 * @param velocitySum        summed amount on this account within the velocity window (incl. this one)
 * @param structuringCount   number of sub-threshold deposits on this account within the structuring window
 * @param structuringSum     summed amount of those sub-threshold deposits within the structuring window
 * @param priorCountry       country of this account's previous transaction, or {@code null} if none/unknown
 * @param priorOccurredAtMs  epoch-millis of this account's previous transaction, or {@code null} if none
 */
public record ScoringFact(
        TransactionReceived transaction,
        int velocityCount,
        BigDecimal velocitySum,
        int structuringCount,
        BigDecimal structuringSum,
        String priorCountry,
        Long priorOccurredAtMs) {}
