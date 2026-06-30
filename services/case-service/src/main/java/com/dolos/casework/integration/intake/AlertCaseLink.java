package com.dolos.casework.integration.intake;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The idempotency record linking an {@code AlertRaised} to the case it opened (Phase 3E). Keyed on the
 * alert id, it lets the intake consumer open at most one case per alert: a redelivery finds the link
 * already present and skips. Written in the same transaction as the {@code OpenCase} command, so a case
 * is never left without its link (and vice versa).
 */
@Entity
@Table(name = "alert_case_link")
public class AlertCaseLink {

    @Id
    @Column(name = "alert_id", nullable = false, updatable = false)
    private UUID alertId;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    protected AlertCaseLink() {} // for JPA

    public AlertCaseLink(UUID alertId, UUID caseId, Instant openedAt) {
        this.alertId = alertId;
        this.caseId = caseId;
        this.openedAt = openedAt;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }
}
