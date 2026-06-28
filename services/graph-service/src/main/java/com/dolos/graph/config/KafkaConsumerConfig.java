package com.dolos.graph.config;

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
 * Kafka consumer wiring for graph-service (Phase 2D): reads the header-less JSON
 * {@code TransactionReceived} events, so the value type is fixed here rather than read from a
 * header. An {@link ErrorHandlingDeserializer} stops a poison record from wedging the partition, and
 * a bounded-retry {@link DefaultErrorHandler} retries transient failures before skipping.
 *
 * <p>The listener runs on <b>virtual threads</b> (Loom): MERGEing into Neo4j is blocking Bolt I/O,
 * and virtual threads keep that cheap. Concurrency stays at one consumer thread per container (the
 * Kafka client is not thread-safe); the virtual thread just avoids pinning a platform thread on I/O.
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
        // Drain the topic backlog the first time this group runs, so transactions received before
        // graph-service existed still land in the graph.
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // The listener container commits offsets after a successful handle — not the client.
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

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
                config, new StringDeserializer(), new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionReceived>
            kafkaListenerContainerFactory(
                    ConsumerFactory<String, TransactionReceived> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, TransactionReceived> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Retry a failing record a few times (transient Bolt blips), then log-and-skip so the
        // partition keeps moving. MERGE is idempotent, so a redelivery can't double-write.
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));

        // Run the listener on a virtual thread (see class javadoc).
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("graph-consumer-");
        executor.setVirtualThreads(true);
        factory.getContainerProperties().setListenerTaskExecutor(executor);

        return factory;
    }
}
