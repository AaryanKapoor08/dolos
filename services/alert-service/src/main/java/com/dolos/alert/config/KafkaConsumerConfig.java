package com.dolos.alert.config;

import com.dolos.events.RingDetected;
import com.dolos.events.RiskScored;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * Kafka consumer wiring for alert-service (Phase 1E): reads the header-less JSON
 * {@code RiskScored} events, so the value type is fixed here rather than read from a header. An
 * {@link ErrorHandlingDeserializer} stops a poison record from wedging the partition, and a
 * bounded-retry {@link DefaultErrorHandler} retries transient failures before skipping.
 *
 * <p>The listener runs on <b>virtual threads</b> (Loom): persisting an alert is blocking JDBC, and
 * virtual threads keep that cheap. Concurrency stays at one consumer thread per container (the Kafka
 * client is not thread-safe); the virtual thread just avoids pinning a platform thread on JDBC.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, RiskScored> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // Drain the topic backlog the first time this group runs, so scores published before the
        // consumer existed still get a chance to raise an alert.
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // The listener container commits offsets after a successful handle — not the client.
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ObjectMapper mapper =
                JsonMapper.builder()
                        .addModule(new JavaTimeModule())
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
        // useHeadersIfPresent=false: scoring strips type headers, so always target this type.
        JsonDeserializer<RiskScored> jsonDeserializer =
                new JsonDeserializer<>(RiskScored.class, mapper, false);
        jsonDeserializer.addTrustedPackages("com.dolos.events");

        return new DefaultKafkaConsumerFactory<>(
                config, new StringDeserializer(), new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RiskScored> kafkaListenerContainerFactory(
            ConsumerFactory<String, RiskScored> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, RiskScored> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Retry a failing record a few times (transient DB blips), then log-and-skip so the
        // partition keeps moving. Duplicate-key races are handled idempotently in AlertService.
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));

        // Run the listener on a virtual thread (see class javadoc).
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("alert-consumer-");
        executor.setVirtualThreads(true);
        factory.getContainerProperties().setListenerTaskExecutor(executor);

        // Distributed tracing (Phase 6A): this is a hand-built factory, so Boot's
        // spring.kafka.listener.observation-enabled property does NOT reach it. Enable observation so the
        // listener extracts the `traceparent` header and continues the trace begun at ingestion.
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }

    // --- RingDetected consumer (Phase 2E) ---------------------------------------------------------
    // A second factory pair typed to RingDetected, on the same own group, draining the backlog so a
    // ring detected before this service restarted still escalates. Same header-less-JSON +
    // ErrorHandlingDeserializer + bounded-retry conventions as the RiskScored path above.

    @Bean
    public ConsumerFactory<String, RingDetected> ringConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ObjectMapper mapper =
                JsonMapper.builder()
                        .addModule(new JavaTimeModule())
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
        JsonDeserializer<RingDetected> jsonDeserializer =
                new JsonDeserializer<>(RingDetected.class, mapper, false);
        jsonDeserializer.addTrustedPackages("com.dolos.events");

        return new DefaultKafkaConsumerFactory<>(
                config, new StringDeserializer(), new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RingDetected>
            ringKafkaListenerContainerFactory(
                    @Qualifier("ringConsumerFactory") ConsumerFactory<String, RingDetected> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, RingDetected> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));

        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("alert-ring-consumer-");
        executor.setVirtualThreads(true);
        factory.getContainerProperties().setListenerTaskExecutor(executor);

        // Distributed tracing (Phase 6A): join the trace on the RingDetected path too (see above).
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }
}
