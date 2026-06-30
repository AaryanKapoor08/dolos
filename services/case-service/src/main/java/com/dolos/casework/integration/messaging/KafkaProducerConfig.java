package com.dolos.casework.integration.messaging;

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
 * Kafka producer wiring for the case-event outbox (Phase 3E). Identical conventions to the other Dolos
 * producers: keys are case ids (String) for per-case partition ordering; values are {@code dolos-events}
 * records serialized as header-less ISO-8601 JSON ({@code setAddTypeInfo(false)}); idempotent + acks=all
 * so a retry can't silently duplicate or drop an event.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> caseProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // Fail fast when the broker is down: the durability guarantee is the Modulith outbox, not an
        // in-producer buffer. A bounded delivery timeout makes a blip surface promptly as a failed
        // send (-> the publication stays incomplete and is resubmitted on restart) instead of the
        // record lingering in the accumulator and being delivered late as a duplicate.
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 4000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 8000);

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
    public KafkaTemplate<String, Object> caseKafkaTemplate(
            ProducerFactory<String, Object> caseProducerFactory) {
        return new KafkaTemplate<>(caseProducerFactory);
    }
}
