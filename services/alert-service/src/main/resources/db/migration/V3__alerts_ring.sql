-- Phase 2E: let alert-service raise alerts for graph rings (RingDetected), not just scored
-- transactions. A ring has no single transaction id, so generalise the idempotency key.
--
-- New migration (never edit an applied V1/V2). `dedupe_key` is the unified idempotency key:
--   - TRANSACTION alerts -> the transaction id (as text)
--   - RING alerts        -> the ringId
-- `transaction_id` becomes nullable (ring alerts have none); its UNIQUE constraint stays (Postgres
-- treats NULLs as distinct, so many ring alerts coexist). Phase 2F builds the CQRS read model on top.

ALTER TABLE alerts ALTER COLUMN transaction_id DROP NOT NULL;
ALTER TABLE alerts ADD COLUMN alert_type VARCHAR(16) NOT NULL DEFAULT 'TRANSACTION';
ALTER TABLE alerts ADD COLUMN dedupe_key VARCHAR(128);

-- Backfill existing rows: their idempotency key was the transaction id.
UPDATE alerts SET dedupe_key = transaction_id::text WHERE dedupe_key IS NULL;

ALTER TABLE alerts ALTER COLUMN dedupe_key SET NOT NULL;
ALTER TABLE alerts ADD CONSTRAINT uq_alerts_dedupe_key UNIQUE (dedupe_key);
