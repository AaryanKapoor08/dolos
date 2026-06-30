package com.dolos.casework.casequery.readmodel;

import com.dolos.casework.casecmd.CaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The current-state CQRS read model for a case (Phase 3C), mapped to {@code case_view} (owned by the
 * Flyway migration {@code V2__case_view.sql} in the {@code casework} schema). Built and kept up to
 * date by the {@code CaseProjection} event handler from the Axon event stream; never written to
 * directly by a command. Internal to {@code casequery}.
 */
@Entity
@Table(name = "case_view")
public class CaseView {

    @Id
    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CaseStatus status;

    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(nullable = false)
    private int score;

    @Column(length = 128)
    private String assignee;

    @Column(name = "opened_by", nullable = false, length = 128)
    private String openedBy;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "report_reference", length = 128)
    private String reportReference;

    @Column(length = 256)
    private String resolution;

    /** Required by JPA. */
    protected CaseView() {}

    public CaseView(
            UUID caseId,
            CaseStatus status,
            UUID alertId,
            String accountId,
            int score,
            String openedBy,
            Instant openedAt) {
        this.caseId = caseId;
        this.status = status;
        this.alertId = alertId;
        this.accountId = accountId;
        this.score = score;
        this.openedBy = openedBy;
        this.openedAt = openedAt;
        this.updatedAt = openedAt;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public String getAccountId() {
        return accountId;
    }

    public int getScore() {
        return score;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getOpenedBy() {
        return openedBy;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getReportReference() {
        return reportReference;
    }

    public void setReportReference(String reportReference) {
        this.reportReference = reportReference;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }
}
