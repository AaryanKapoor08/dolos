/*
 * dolos-common — shared, framework-agnostic building blocks reused across services
 * (value objects, identifiers, error envelopes, constants). Plain Java library: no
 * Spring here, so any service can depend on it without dragging in a framework.
 *
 * Populated with shared types (Money, AccountId, error envelope) as services need them.
 */
plugins {
    id("dolos.java-conventions")
}
