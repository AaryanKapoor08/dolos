-- Phase 2A: carry the originating country of a raw inbound transaction.
--
-- Added as a new migration (never edit an already-applied V1 — that changes its Flyway checksum and
-- fails validation on boot). Nullable + no default: historical rows simply have no country, and
-- ingestion only sets it when the caller supplies one. scoring-service's stateful topology reads
-- this (via TransactionReceived.country) to track an account's last-seen location for the
-- impossible-travel typology.

ALTER TABLE raw_transactions ADD COLUMN country VARCHAR(2);
