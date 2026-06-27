package com.dolos.scoring.streams;

/**
 * The last location and time an account was seen transacting, held per account in a Kafka Streams
 * key-value store. The next transaction compares against this to detect impossible travel (a country
 * change in an implausibly short time). Persisted as header-less JSON via {@link JsonSerde}.
 *
 * @param country      ISO-3166 alpha-2 country of the prior transaction, may be {@code null}
 * @param occurredAtMs epoch-millis the prior transaction occurred
 */
public record LastSeen(String country, long occurredAtMs) {}
