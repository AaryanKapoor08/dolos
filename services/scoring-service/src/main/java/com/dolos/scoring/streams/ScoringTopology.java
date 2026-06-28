package com.dolos.scoring.streams;

import com.dolos.events.RiskScored;
import com.dolos.events.Topics;
import com.dolos.events.TransactionReceived;
import com.dolos.scoring.service.RiskScoringEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.Stores;

/**
 * Declares the Phase 2A scoring topology onto a {@link StreamsBuilder}:
 *
 * <pre>
 *   transactions.received ──▶ [ScoringProcessor + 3 state stores] ──▶ risk.scored
 * </pre>
 *
 * The three stores (velocity window, structuring window, last-seen key-value) are in-memory: state
 * is rebuilt by replaying the source topic from {@code earliest} on restart, which keeps the demo
 * free of RocksDB native dependencies and lets the {@code TopologyTestDriver} test run as pure JVM.
 *
 * <p>Kept as a plain class (not a Spring component) so the exact same {@code buildPipeline} runs
 * under both Spring and the unit test — the test asserts the real wiring, not a stand-in.
 */
public class ScoringTopology {

    private final RiskScoringEngine engine;
    private final ObjectMapper mapper;

    public ScoringTopology(RiskScoringEngine engine, ObjectMapper mapper) {
        this.engine = engine;
        this.mapper = mapper;
    }

    public void buildPipeline(StreamsBuilder builder) {
        Serde<String> keySerde = Serdes.String();
        Serde<TransactionReceived> txnSerde = new JsonSerde<>(mapper, TransactionReceived.class);
        Serde<RiskScored> scoredSerde = new JsonSerde<>(mapper, RiskScored.class);
        Serde<BigDecimal> amountSerde = new JsonSerde<>(mapper, BigDecimal.class);
        Serde<LastSeen> lastSeenSerde = new JsonSerde<>(mapper, LastSeen.class);
        Serde<Boolean> payeeSerde = new JsonSerde<>(mapper, Boolean.class);

        builder.addStateStore(
                Stores.windowStoreBuilder(
                        Stores.inMemoryWindowStore(
                                ScoringProcessor.VELOCITY_STORE,
                                RiskScoringEngine.VELOCITY_WINDOW,
                                RiskScoringEngine.VELOCITY_WINDOW,
                                true),
                        keySerde,
                        amountSerde));
        builder.addStateStore(
                Stores.windowStoreBuilder(
                        Stores.inMemoryWindowStore(
                                ScoringProcessor.STRUCTURING_STORE,
                                RiskScoringEngine.STRUCTURING_WINDOW,
                                RiskScoringEngine.STRUCTURING_WINDOW,
                                true),
                        keySerde,
                        amountSerde));
        builder.addStateStore(
                Stores.keyValueStoreBuilder(
                        Stores.inMemoryKeyValueStore(ScoringProcessor.LAST_SEEN_STORE),
                        keySerde,
                        lastSeenSerde));
        builder.addStateStore(
                Stores.keyValueStoreBuilder(
                        Stores.inMemoryKeyValueStore(ScoringProcessor.PAYEE_STORE),
                        keySerde,
                        payeeSerde));

        builder.stream(Topics.TRANSACTIONS_RECEIVED, Consumed.with(keySerde, txnSerde))
                .process(
                        () -> new ScoringProcessor(engine),
                        ScoringProcessor.VELOCITY_STORE,
                        ScoringProcessor.STRUCTURING_STORE,
                        ScoringProcessor.LAST_SEEN_STORE,
                        ScoringProcessor.PAYEE_STORE)
                .to(Topics.RISK_SCORED, Produced.with(keySerde, scoredSerde));
    }

    /** The shared header-less JSON mapper used for the wire and the state stores. */
    public static ObjectMapper headerlessMapper() {
        return com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(
                        com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(
                        com.fasterxml.jackson.databind.DeserializationFeature
                                .FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
