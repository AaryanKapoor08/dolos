-- Phase 0D: canonical transactions table (system of record).
-- Schema is owned here (Flyway); the JPA entity TransactionEntity must match it.

CREATE TABLE transactions (
    id                       UUID          PRIMARY KEY,
    account_id               VARCHAR(64)   NOT NULL,
    counterparty_account_id  VARCHAR(64),
    amount                   NUMERIC(19, 4) NOT NULL,
    currency                 VARCHAR(3)    NOT NULL,
    direction                VARCHAR(8)    NOT NULL,
    description              VARCHAR(255),
    occurred_at              TIMESTAMPTZ   NOT NULL,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Common access paths: by account and by time (used by later scoring/alerting phases).
CREATE INDEX idx_transactions_account_id ON transactions (account_id);
CREATE INDEX idx_transactions_occurred_at ON transactions (occurred_at);
