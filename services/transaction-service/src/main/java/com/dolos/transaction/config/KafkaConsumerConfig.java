package com.dolos.transaction.config;

import com.dolos.events.TransactionReceived;
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
 * Explicit Kafka consumer wiring for transaction-service (Phase 1C). Reads the JSON
 * {@code TransactionReceived} events that ingestion-service publishes (no Java type headers on the
 * wire), so the value type is fixed here rather than read from a header.
 *
 * <p>Two deliberate choices:
 * <ul>
 *   <li><b>ErrorHandlingDeserializer</b> wraps the JSON deserializer so a single poison record
 *       can't wedge the partition in an infinite deserialize-fail loop — it is handed to the error
 *       handler instead.</li>
 *   <li>The listener runs on <b>virtual threads</b> (Loom): persisting the canonical transaction is
 *       blocking JDBC, and virtual threads keep that cheap at high fan-in. Concurrency stays at one
 *       consumer thread per container (the Kafka client is not thread-safe); the virtual thread just
 *       avoids tying up a platform thread while JDBC blocks.</li>
 * </ul>
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, TransactionReceived> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // Read from the start the first time this group runs, so events published before the
        // consumer existed are still persisted.
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // The listener container commits offsets after a successful handle — not the client.
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Mirror the producer's mapper: ISO-8601 java.time, tolerate unknown fields so the contract
        // can add fields without breaking older consumers.
        ObjectMapper mapper =
                JsonMapper.builder()
                        .addModule(new JavaTimeModule())
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
        // useHeadersIfPresent=false: ingestion strips type headers, so always target this type.
        JsonDeserializer<TransactionReceived> jsonDeserializer =
                new JsonDeserializer<>(TransactionReceived.class, mapper, false);
        jsonDeserializer.addTrustedPackages("com.dolos.events");

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionReceived>
            kafkaListenerContainerFactory(ConsumerFactory<String, TransactionReceived> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, TransactionReceived> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Retry a failing record a few times (transient DB blips), then log-and-skip so the
        // partition keeps moving. Duplicate-key races are handled idempotently upstream.
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));

        // Run the listener on a virtual thread (see class javadoc).
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("txn-consumer-");
        executor.setVirtualThreads(true);
        factory.getContainerProperties().setListenerTaskExecutor(executor);

        return factory;
    }
}
