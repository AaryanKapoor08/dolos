-- Phase 1E: alerts raised when a RiskScored crosses the configured score threshold.
--
-- Owned solely by alert-service in its own `alert` schema (Flyway creates and migrates it, with
-- its history table there too), isolated from `public` so multiple services can share one
-- database without colliding.

CREATE TABLE alerts (
    id              UUID           PRIMARY KEY,
    transaction_id  UUID           NOT NULL UNIQUE,   -- idempotency key: one alert per scored txn
    account_id      VARCHAR(64)    NOT NULL,
    score           INTEGER        NOT NULL,
    reasons         TEXT           NOT NULL DEFAULT '',  -- newline-delimited reason strings
    raised_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Supports the risk-sorted alert queue (GET /api/alerts, default sort score DESC).
CREATE INDEX idx_alerts_score ON alerts (score DESC);
CREATE INDEX idx_alerts_account_id ON alerts (account_id);
