-- Phase 3C: the CQRS read model for cases.
--
-- The Axon event store (V1) is the write side / source of truth. These tables are a denormalized
-- projection of it, built by the CaseProjection event handler (a tracking event processor whose
-- position is stored in token_entry), so the UI can read current case state + the full timeline
-- without replaying events. Rebuildable from the event store at any time, so they own no truth.

-- Current state of each case (one row per case).
CREATE TABLE case_view (
    case_id           UUID         PRIMARY KEY,
    status            VARCHAR(16)  NOT NULL,
    alert_id          UUID,                          -- the alert that opened the case (nullable)
    account_id        VARCHAR(64)  NOT NULL,
    score             INTEGER      NOT NULL,
    assignee          VARCHAR(128),                  -- null until assigned
    opened_by         VARCHAR(128) NOT NULL,
    opened_at         TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    report_reference  VARCHAR(128),                  -- set once a report is filed
    resolution        VARCHAR(256)                   -- set once closed
);

-- The investigation queue scans most-recently-updated first, and filters by status.
CREATE INDEX idx_case_view_updated ON case_view (updated_at DESC);
CREATE INDEX idx_case_view_status ON case_view (status);

-- One row per domain event = the case timeline. The primary key is the Axon event message id, so a
-- projection replay re-projects each event to the same row (idempotent). (case_id, sequence) is the
-- stream position, used to order the timeline.
CREATE TABLE case_timeline (
    event_id     VARCHAR(64)  PRIMARY KEY,
    case_id      UUID         NOT NULL,
    sequence     BIGINT       NOT NULL,
    type         VARCHAR(32)  NOT NULL,
    summary      VARCHAR(512) NOT NULL,
    actor        VARCHAR(128) NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_case_timeline_stream UNIQUE (case_id, sequence)
);

CREATE INDEX idx_case_timeline_case ON case_timeline (case_id, sequence);
