package com.dolos.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An investigation case was closed (Phase 3E), published to {@link Topics#CASES_CLOSED} via the
 * Modulith outbox. Integration contract for the service-internal Axon {@code CaseClosed} domain event;
 * the terminal event in a case's life.
 *
 * @param caseId      the case id
 * @param resolution  how it was resolved
 * @param closedBy    who closed it
 * @param closedAt    when it was closed
 */
public record CaseClosed(UUID caseId, String resolution, String closedBy, Instant closedAt) {

    public CaseClosed {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(closedBy, "closedBy must not be null");
        Objects.requireNonNull(closedAt, "closedAt must not be null");
    }
}
