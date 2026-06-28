-- Phase 2F: CQRS read model for the analyst alert queue.
--
-- `alerts` stays the normalized write model / source of truth (idempotency guarded by its unique
-- dedupe_key). `alert_view` is a denormalized, query-optimized projection built from it: it carries a
-- precomputed severity bucket and a one-line title so the queue renders without any joins or
-- post-processing, and is indexed for the default risk-sorted scan. Rebuildable from the write model
-- at any time, so it owns no truth of its own.

CREATE TABLE alert_view (
    alert_id        UUID           PRIMARY KEY,
    dedupe_key      VARCHAR(128)   NOT NULL UNIQUE,   -- mirrors the write-model idempotency key
    alert_type      VARCHAR(16)    NOT NULL,
    transaction_id  UUID,                              -- null for ring alerts
    account_id      VARCHAR(64)    NOT NULL,
    score           INTEGER        NOT NULL,
    severity        VARCHAR(8)     NOT NULL,           -- HIGH / MEDIUM / LOW (precomputed from score)
    title           VARCHAR(256)   NOT NULL,           -- denormalized one-line headline
    reasons         TEXT           NOT NULL DEFAULT '',
    detail          TEXT,
    raised_at       TIMESTAMPTZ    NOT NULL
);

-- The analyst queue scans highest-risk-first, newest-first — one covering index serves it.
CREATE INDEX idx_alert_view_queue ON alert_view (score DESC, raised_at DESC);
CREATE INDEX idx_alert_view_severity ON alert_view (severity);
