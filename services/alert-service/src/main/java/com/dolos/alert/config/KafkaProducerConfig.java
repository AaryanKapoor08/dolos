package com.dolos.alert.config;

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
 * Kafka producer wiring for alert-service (Phase 1E): publishes {@code AlertRaised} keyed by
 * account id (per-account partition ordering). Idempotent + acks=all so a retry can't silently
 * duplicate or drop. The value serializer writes clean, header-less, ISO-8601 JSON (matching the
 * rest of the platform's wire convention).
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
        // Distributed tracing (Phase 6A): hand-built template, so Boot's observation property does not
        // reach it. Enable observation so publishing AlertRaised injects the `traceparent` header and the
        // trace continues from the RiskScored consumer span all the way into case-service's OpenCase.
        template.setObservationEnabled(true);
        return template;
    }
}
