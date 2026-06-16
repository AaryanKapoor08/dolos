/**
 * The Dolos Kafka integration schema: event records exchanged between services over the
 * broker. These records are the single source of truth for the wire contract — producers
 * and consumers depend on this package, never directly on one another.
 *
 * <p>Conventions: events are immutable {@code record}s carrying primitive/JDK types (so
 * they serialize cleanly as JSON with no shared-enum coupling); topic names live in
 * {@link com.dolos.events.Topics}.
 */
package com.dolos.events;
