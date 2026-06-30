package com.dolos.casework.casequery.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry in a case's timeline — a projection of a single domain event (Phase 3C), mapped to
 * {@code case_timeline}. The primary key is the Axon event message id, so re-projecting the stream
 * (a replay) upserts each entry to the same row rather than duplicating it. Internal to
 * {@code casequery}.
 */
@Entity
@Table(name = "case_timeline")
public class CaseTimelineEntry {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 64)
    private String eventId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(nullable = false)
    private long sequence;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 512)
    private String summary;

    @Column(nullable = false, length = 128)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Required by JPA. */
    protected CaseTimelineEntry() {}

    public CaseTimelineEntry(
            String eventId,
            UUID caseId,
            long sequence,
            String type,
            String summary,
            String actor,
            Instant occurredAt) {
        this.eventId = eventId;
        this.caseId = caseId;
        this.sequence = sequence;
        this.type = type;
        this.summary = summary;
        this.actor = actor;
        this.occurredAt = occurredAt;
    }

    public String getEventId() {
        return eventId;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public long getSequence() {
        return sequence;
    }

    public String getType() {
        return type;
    }

    public String getSummary() {
        return summary;
    }

    public String getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
