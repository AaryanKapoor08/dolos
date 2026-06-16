/*
 * dolos-events — the Kafka integration schema as plain Java records.
 *
 * This is the contract between producers and consumers: services depend on these event
 * records, never on each other. Like dolos-common it is framework-agnostic (no Spring,
 * no Kafka client) so any module can depend on it freely. Serialization (JSON via Jackson)
 * is configured by each service, not here.
 *
 * New event types are added here as later phases need them (RingDetected, CaseOpened, …).
 */
plugins {
    id("dolos.java-conventions")
}
