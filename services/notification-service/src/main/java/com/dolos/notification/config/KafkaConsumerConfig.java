package com.dolos.notification.config;

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
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer wiring for notification-service (Phase 5C). Unlike the typed factories elsewhere, this
 * service consumes four different event types off four topics purely to re-broadcast them, so it reads
 * each record as a raw JSON {@code String} through one container factory and parses to the specific
 * record in the listener with the shared {@link #eventObjectMapper() mapper}. That keeps the wiring to
 * a single factory instead of one pair per event type.
 *
 * <p>Same house conventions as the other consumers: {@code earliest} so a browser that connects after
 * an event still triggers on the backlog drain, manual offset commit by the container, a bounded-retry
 * {@link DefaultErrorHandler} so a poison record can't wedge a partition, and the listener running on a
 * <b>virtual thread</b> (the STOMP fan-out is cheap but this matches the platform style).
 */
@Configuration
public class KafkaConsumerConfig {

    /** Jackson mapper the listeners share to parse the header-less JSON event payloads. */
    @Bean
    public ObjectMapper eventObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(
                config, new StringDeserializer(), new StringDeserializer());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));

        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("notify-consumer-");
        executor.setVirtualThreads(true);
        factory.getContainerProperties().setListenerTaskExecutor(executor);
        return factory;
    }
}
