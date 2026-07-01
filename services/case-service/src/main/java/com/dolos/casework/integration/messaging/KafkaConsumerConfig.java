package com.dolos.casework.integration.messaging;

import com.dolos.events.AlertRaised;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer wiring for the {@code integration} module (Phase 3E): reads the header-less JSON
 * {@code AlertRaised} events, so the value type is fixed here rather than read from a header. This
 * mirrors the alert-service pattern exactly — an {@link ErrorHandlingDeserializer} keeps a poison
 * record from wedging the partition, and a bounded-retry {@link DefaultErrorHandler} retries transient
 * failures before skipping.
 *
 * <p>The listener runs on a <b>virtual thread</b>: opening a case is blocking JDBC (event store +
 * dedupe row), and virtual threads keep that cheap without pinning a platform thread.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, AlertRaised> alertConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // Drain the backlog the first time this group runs, so alerts raised before case-service
        // existed still get a chance to open a case.
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // The listener container commits offsets after a successful handle — not the client.
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ObjectMapper mapper =
                JsonMapper.builder()
                        .addModule(new JavaTimeModule())
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
        // useHeadersIfPresent=false: alert-service strips type headers, so always target this type.
        JsonDeserializer<AlertRaised> jsonDeserializer =
                new JsonDeserializer<>(AlertRaised.class, mapper, false);
        jsonDeserializer.addTrustedPackages("com.dolos.events");

        return new DefaultKafkaConsumerFactory<>(
                config, new StringDeserializer(), new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AlertRaised> alertListenerContainerFactory(
            ConsumerFactory<String, AlertRaised> alertConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, AlertRaised> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(alertConsumerFactory);

        // Retry a failing record a few times (transient DB blips), then log-and-skip so the partition
        // keeps moving. Re-opening is idempotent (alert id -> case), so a redelivery is harmless.
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));

        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("case-intake-");
        executor.setVirtualThreads(true);
        factory.getContainerProperties().setListenerTaskExecutor(executor);

        // Distributed tracing (Phase 6A): this is a hand-built factory, so Boot's
        // spring.kafka.listener.observation-enabled property does NOT reach it. Enable observation so the
        // listener extracts the `traceparent` header and continues the trace all the way to OpenCase.
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }
}
