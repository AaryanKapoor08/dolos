-- Phase 2D: carry the owning customer and originating device of a raw inbound transaction.
--
-- Added as a new migration (never edit an already-applied V1/V2 — that changes its Flyway checksum
-- and fails validation on boot). Both nullable + no default: historical rows simply have neither, and
-- ingestion only sets them when the caller supplies them. graph-service reads these (via
-- TransactionReceived.customerId / .deviceId) to MERGE the (:Customer)-[:OWNS]->(:Account) and
-- (:Account)-[:USED]->(:Device) edges of the fraud graph.

ALTER TABLE raw_transactions ADD COLUMN customer_id VARCHAR(64);
ALTER TABLE raw_transactions ADD COLUMN device_id VARCHAR(64);
