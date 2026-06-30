package com.dolos.casework.casequery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The query-side projection of a case (Phase 3C): current state plus its full timeline. This is the
 * contract returned by the {@code FindCaseById}/{@code FindAllCases} query handlers and pushed to
 * subscription-query subscribers on every update (consumed by notifications in Phase 5).
 *
 * @param caseId          the case id
 * @param status          current lifecycle status
 * @param alertId         the alert that opened the case (may be {@code null})
 * @param accountId       the subject account
 * @param score           the risk score the case was opened with
 * @param assignee        the current assignee ({@code null} until assigned)
 * @param openedBy        who opened the case
 * @param openedAt        when it was opened
 * @param updatedAt       when it last changed
 * @param reportReference the filed report reference ({@code null} until a report is filed)
 * @param resolution      the closing disposition ({@code null} until closed)
 * @param timeline        every event applied to the case, in order
 */
public record CaseDetails(
        UUID caseId,
        String status,
        UUID alertId,
        String accountId,
        int score,
        String assignee,
        String openedBy,
        Instant openedAt,
        Instant updatedAt,
        String reportReference,
        String resolution,
        List<TimelineItem> timeline) {

    /** One entry in the case timeline. */
    public record TimelineItem(
            long sequence, String type, String summary, String actor, Instant occurredAt) {}
}
