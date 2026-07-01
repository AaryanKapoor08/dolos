package com.dolos.notification.api;

import java.time.Instant;

/**
 * The frame pushed to STOMP subscribers (Phase 5C). A small, UI-shaped envelope so the console can
 * render a live feed item (a toast / queue row) without re-fetching: a {@code kind} discriminator,
 * the subject ids, a one-line human {@code title}, and when it happened. The full detail is still a
 * BFF/REST call away — this is a nudge, not the source of truth.
 *
 * @param kind      event discriminator: {@code ALERT_RAISED}, {@code CASE_OPENED},
 *                  {@code CASE_ESCALATED}, {@code CASE_CLOSED}
 * @param entityId  the primary subject id (alertId or caseId) as a string
 * @param accountId the subject account, when the source event carries one (may be {@code null})
 * @param title     a one-line headline for the feed
 * @param at        when the notification was produced
 */
public record Notification(
        String kind, String entityId, String accountId, String title, Instant at) {}
