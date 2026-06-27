package com.dolos.scoring.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/**
 * A header-less JSON {@link Serde} over a shared Jackson {@link ObjectMapper}, used for both the
 * Kafka wire (TransactionReceived in, RiskScored out) and the in-memory state stores. It writes
 * plain JSON bytes with no type headers — matching the platform's wire convention so the same bytes
 * are readable by every other service and by Redpanda Console.
 *
 * <p>Bound to a concrete type at construction (Kafka Streams operators always know their value type),
 * so there is no ambiguity on read. {@code null} round-trips as {@code null}.
 */
public final class JsonSerde<T> implements Serde<T> {

    private final ObjectMapper mapper;
    private final Class<T> type;

    public JsonSerde(ObjectMapper mapper, Class<T> type) {
        this.mapper = mapper;
        this.type = type;
    }

    @Override
    public Serializer<T> serializer() {
        return new Serializer<>() {
            @Override
            public byte[] serialize(String topic, T data) {
                if (data == null) {
                    return null;
                }
                try {
                    return mapper.writeValueAsBytes(data);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to serialize " + type.getSimpleName(), e);
                }
            }
        };
    }

    @Override
    public Deserializer<T> deserializer() {
        return new Deserializer<>() {
            @Override
            public T deserialize(String topic, byte[] data) {
                if (data == null) {
                    return null;
                }
                try {
                    return mapper.readValue(data, type);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to deserialize " + type.getSimpleName(), e);
                }
            }
        };
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // Nothing to configure: the mapper and target type are supplied at construction.
    }
}
