package com.dolos.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An investigation case was opened (Phase 3E), published by case-service to {@link Topics#CASES_OPENED}
 * from the event-sourced {@code Case} aggregate via the Spring Modulith transactional outbox. This is
 * the integration contract — distinct from the service-internal Axon {@code CaseOpened} domain event.
 * Consumed by later phases (notifications, the UI's live case feed).
 *
 * @param caseId    the case id
 * @param alertId   the alert that opened it (may be {@code null} when opened manually)
 * @param accountId the subject account
 * @param score     the risk score carried from the alert
 * @param openedBy  who/what opened the case
 * @param openedAt  when it was opened
 */
public record CaseOpened(
        UUID caseId,
        UUID alertId,
        String accountId,
        int score,
        String openedBy,
        Instant openedAt) {

    public CaseOpened {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(openedBy, "openedBy must not be null");
        Objects.requireNonNull(openedAt, "openedAt must not be null");
    }
}
