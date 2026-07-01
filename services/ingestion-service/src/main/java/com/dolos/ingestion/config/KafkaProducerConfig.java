package com.dolos.ingestion.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Explicit Kafka producer wiring for ingestion-service. Keys are account ids (String) so a
 * given account's transactions keep per-partition ordering; values are dolos-events records
 * serialized as JSON. Idempotent + acks=all so a retry can't silently duplicate or drop an event.
 *
 * <p>The value serializer uses a dedicated ObjectMapper that writes {@code java.time} values as
 * ISO-8601 strings (not numeric epochs) and omits Java type headers — so the wire payload is
 * clean, human-readable JSON that any consumer (Spring, NiFi, Redpanda Console) can read.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        ObjectMapper mapper =
                JsonMapper.builder()
                        .addModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build();
        JsonSerializer<Object> valueSerializer = new JsonSerializer<>(mapper);
        valueSerializer.setAddTypeInfo(false);

        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
        // Distributed tracing (Phase 6A): this is a hand-built template, so Boot's
        // spring.kafka.template.observation-enabled property does NOT reach it. Enable observation here
        // so each send() opens a producer span and injects the W3C `traceparent` header onto the record —
        // that header is what lets the downstream consumers (via Kafka Streams) join the same trace.
        template.setObservationEnabled(true);
        return template;
    }
}
