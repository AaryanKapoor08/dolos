-- Phase 1B: raw inbound transactions captured at the reactive edge (ingestion-service).
-- This is the audit trail of *what arrived* (before canonicalisation/scoring). The canonical
-- store of record remains transaction-service's `transactions` table.
--
-- Lives in the shared `dolos` database but is owned solely by ingestion-service (tracked by
-- its own Flyway history table, flyway_schema_history_ingestion).

CREATE TABLE raw_transactions (
    id                       UUID           PRIMARY KEY,   -- == TransactionReceived.transactionId
    account_id               VARCHAR(64)    NOT NULL,
    counterparty_account_id  VARCHAR(64),
    amount                   NUMERIC(19, 4) NOT NULL,
    currency                 VARCHAR(3)     NOT NULL,
    direction                VARCHAR(8)     NOT NULL,
    description              VARCHAR(255),
    occurred_at              TIMESTAMPTZ    NOT NULL,
    received_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    status                   VARCHAR(16)    NOT NULL DEFAULT 'RECEIVED'
);

CREATE INDEX idx_raw_transactions_account_id ON raw_transactions (account_id);
CREATE INDEX idx_raw_transactions_received_at ON raw_transactions (received_at);
