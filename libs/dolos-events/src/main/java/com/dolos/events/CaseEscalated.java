package com.dolos.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An investigation case was escalated to a senior analyst (Phase 3E), published to
 * {@link Topics#CASES_ESCALATED} via the Modulith outbox. Integration contract for the service-internal
 * Axon {@code Escalated} domain event.
 *
 * @param caseId       the case id
 * @param reason       why it was escalated
 * @param escalatedBy  who escalated it
 * @param escalatedAt  when it was escalated
 */
public record CaseEscalated(UUID caseId, String reason, String escalatedBy, Instant escalatedAt) {

    public CaseEscalated {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(escalatedBy, "escalatedBy must not be null");
        Objects.requireNonNull(escalatedAt, "escalatedAt must not be null");
    }
}
