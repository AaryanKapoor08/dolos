-- Phase 3B: the Axon Framework JPA event store + token store (decision G — no Axon Server).
--
-- These are the tables Hibernate maps Axon's built-in entities onto. We own the DDL via Flyway
-- (house rule: ddl-auto=none) rather than letting Hibernate create them, so the schema is explicit
-- and versioned. The column shapes mirror Axon's entities exactly as Hibernate 6.6 + PostgreSQL map
-- them, so the runtime INSERT/SELECT bindings line up:
--   * the serialized payload / metadata / token are @Lob byte[] -> PostgreSQL `oid` (large objects);
--     all Axon access is transactional (Spring-managed UnitOfWork), so oid is safe.
--   * domain_event_entry.global_index is @GeneratedValue -> a sequence; Hibernate's default per-entity
--     name is `domain_event_entry_seq` with allocation size 50, so the sequence increments by 50.

-- Monotonic global index for the event store's streaming/tracking reads.
CREATE SEQUENCE domain_event_entry_seq START WITH 1 INCREMENT BY 50;

-- The append-only domain event log (the source of truth for every aggregate).
CREATE TABLE domain_event_entry (
    global_index          BIGINT       NOT NULL,
    event_identifier      VARCHAR(255) NOT NULL,
    meta_data             OID,
    payload               OID          NOT NULL,
    payload_revision      VARCHAR(255),
    payload_type          VARCHAR(255) NOT NULL,
    time_stamp            VARCHAR(255) NOT NULL,
    aggregate_identifier  VARCHAR(255) NOT NULL,
    sequence_number       BIGINT       NOT NULL,
    type                  VARCHAR(255),
    PRIMARY KEY (global_index),
    CONSTRAINT uk_domain_event_entry_event_id UNIQUE (event_identifier),
    CONSTRAINT uk_domain_event_entry_agg_seq UNIQUE (aggregate_identifier, sequence_number)
);

-- Optional aggregate snapshots (faster rehydration for long event streams).
CREATE TABLE snapshot_event_entry (
    aggregate_identifier  VARCHAR(255) NOT NULL,
    sequence_number       BIGINT       NOT NULL,
    type                  VARCHAR(255) NOT NULL,
    event_identifier      VARCHAR(255) NOT NULL,
    meta_data             OID,
    payload               OID          NOT NULL,
    payload_revision      VARCHAR(255),
    payload_type          VARCHAR(255) NOT NULL,
    time_stamp            VARCHAR(255) NOT NULL,
    PRIMARY KEY (aggregate_identifier, sequence_number, type),
    CONSTRAINT uk_snapshot_event_entry_event_id UNIQUE (event_identifier)
);

-- Tracking tokens: where each streaming event processor (e.g. the CaseView projector, Phase 3C) is
-- in the event stream, so projections survive restarts and can be replayed.
CREATE TABLE token_entry (
    processor_name  VARCHAR(255) NOT NULL,
    segment         INTEGER      NOT NULL,
    token           OID,
    token_type      VARCHAR(255),
    timestamp       VARCHAR(255) NOT NULL,
    owner           VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);
