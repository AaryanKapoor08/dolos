package com.dolos.casework.casecmd;

import java.util.UUID;

/**
 * A new investigation case was opened (typically from a HIGH {@code AlertRaised}, Phase 3E, or an
 * analyst action). The first event in a case's stream. Axon stamps every event with its own
 * timestamp, so the events themselves stay time-free (which keeps them deterministic to test).
 *
 * @param caseId    the case aggregate id
 * @param alertId   the alert that triggered the case
 * @param accountId the subject account under investigation
 * @param score     the risk score that motivated opening the case
 * @param openedBy  who/what opened it (an analyst id, or "system" for auto-opened)
 */
public record CaseOpened(UUID caseId, UUID alertId, String accountId, int score, String openedBy) {}
