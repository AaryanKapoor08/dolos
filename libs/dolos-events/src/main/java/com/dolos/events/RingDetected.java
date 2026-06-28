package com.dolos.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A mule/cash-out ring discovered by graph-service (Phase 2E): a directed cycle of
 * {@code TRANSACTED} edges where money flows A&rarr;B&rarr;C&rarr;&hellip;&rarr;A. Published to
 * {@link Topics#RINGS_DETECTED}; alert-service consumes it to raise/escalate a HIGH-severity alert
 * that no single-transaction rule could surface.
 *
 * @param ringId      stable, rotation-invariant id for the ring (the idempotency key — the same set
 *                    of accounts yields the same id no matter which node detection started from)
 * @param accounts    the accounts on the cycle, in flow order (never {@code null}; may be empty)
 * @param score       the risk score assigned to the ring
 * @param pattern     a human-readable description of the cycle (e.g. {@code "A -> B -> C -> A"})
 * @param hops        the number of hops in the cycle
 * @param detectedAt  when the ring was detected
 */
public record RingDetected(
        String ringId,
        List<String> accounts,
        int score,
        String pattern,
        int hops,
        Instant detectedAt) {

    public RingDetected {
        Objects.requireNonNull(ringId, "ringId must not be null");
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(detectedAt, "detectedAt must not be null");
    }
}
