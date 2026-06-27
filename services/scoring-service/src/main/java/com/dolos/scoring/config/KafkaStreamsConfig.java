package com.dolos.scoring.config;

import com.dolos.scoring.service.RiskScoringEngine;
import com.dolos.scoring.streams.ScoringTopology;
import org.apache.kafka.streams.StreamsBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

/**
 * Kafka Streams wiring for scoring-service (Phase 2A). {@link EnableKafkaStreams} triggers Spring
 * Boot to create the managed {@code StreamsBuilderFactoryBean} from {@code spring.kafka.streams.*}
 * (application id, bootstrap servers, state dir). We then build the {@link ScoringTopology} onto the
 * injected {@link StreamsBuilder} during bean creation.
 *
 * <p>This replaces the v0 plain consumer/producer (Phase 1D): a stream processor with state stores
 * supersedes the single-transaction {@code @KafkaListener}, so those classes are gone.
 */
@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    @Bean
    public ScoringTopology scoringTopology(StreamsBuilder builder, RiskScoringEngine engine) {
        ScoringTopology topology = new ScoringTopology(engine, ScoringTopology.headerlessMapper());
        topology.buildPipeline(builder);
        return topology;
    }
}
