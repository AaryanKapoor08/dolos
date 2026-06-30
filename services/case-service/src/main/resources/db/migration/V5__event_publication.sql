-- Phase 3E: the Spring Modulith JPA event-publication registry — the transactional outbox.
--
-- When the case-event relay publishes an application event inside the command's transaction, Modulith
-- writes a row here in that SAME transaction (alongside the Axon event = the state change). After the
-- @ApplicationModuleListener publishes the event to Kafka, the row's completion_date is set. Rows left
-- incomplete (e.g. the broker was down) are resubmitted on restart, giving at-least-once delivery that
-- pairs with our idempotent consumers. House rule holds: Flyway owns this table (ddl-auto none).
--
-- Column names/types mirror Modulith's JpaEventPublication entity (1.3.x).
CREATE TABLE event_publication (
    id                UUID                     NOT NULL,
    listener_id       TEXT                     NOT NULL,
    event_type        TEXT                     NOT NULL,
    serialized_event  TEXT                     NOT NULL,
    publication_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date   TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

-- The registry scans for still-incomplete publications (completion_date IS NULL) on resubmission,
-- and dedups by the serialized event; index both access paths.
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
