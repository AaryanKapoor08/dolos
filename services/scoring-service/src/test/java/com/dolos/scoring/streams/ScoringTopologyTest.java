package com.dolos.scoring.streams;

import static org.assertj.core.api.Assertions.assertThat;

import com.dolos.events.RiskScored;
import com.dolos.events.Topics;
import com.dolos.events.TransactionReceived;
import com.dolos.scoring.service.RiskScoringEngine;
import com.dolos.scoring.service.ScoreCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/**
 * Drives the real {@link ScoringTopology} with a {@code TopologyTestDriver} — no broker — to prove
 * the stateful typologies fire end-to-end through the state stores, not just in the engine. This is
 * the Phase 2A Definition of Done: a burst of sub-$10k deposits from one account within a day
 * triggers a structuring score the v0 single-transaction engine could never produce.
 */
class ScoringTopologyTest {

    private static final Instant T0 = Instant.parse("2026-02-02T08:00:00Z");
    private final ObjectMapper mapper = ScoringTopology.headerlessMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, TransactionReceived> input;
    private TestOutputTopic<String, RiskScored> output;

    @BeforeEach
    void setUp(@TempDir Path stateDir) {
        StreamsBuilder builder = new StreamsBuilder();
        new ScoringTopology(new RiskScoringEngine(), new ScoreCache(), mapper).buildPipeline(builder);
        Topology topology = builder.build();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "scoring-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.STATE_DIR_CONFIG, stateDir.toString());

        driver = new TopologyTestDriver(topology, props);
        input =
                driver.createInputTopic(
                        Topics.TRANSACTIONS_RECEIVED,
                        new StringSerializer(),
                        new JsonSerde<>(mapper, TransactionReceived.class).serializer());
        output =
                driver.createOutputTopic(
                        Topics.RISK_SCORED,
                        new StringDeserializer(),
                        new JsonSerde<>(mapper, RiskScored.class).deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    private void send(String account, String amount, String country, Instant occurredAt) {
        TransactionReceived txn =
                new TransactionReceived(
                        UUID.randomUUID(),
                        account,
                        "CP",
                        new BigDecimal(amount),
                        "CAD",
                        "CREDIT",
                        "feed",
                        country,
                        null,
                        null,
                        occurredAt,
                        occurredAt.plusSeconds(1));
        input.pipeInput(account, txn, occurredAt.toEpochMilli());
    }

    @Test
    void burstOfSubThresholdDeposits_triggersStructuring() {
        // Four $2,400 deposits a few minutes apart — each well under $10k, summing to $9,600.
        for (int i = 0; i < 4; i++) {
            send("ACC-STRUCT", "2400.00", "CA", T0.plus(Duration.ofMinutes(i * 5L)));
        }

        List<RiskScored> scores = output.readValuesToList();
        assertThat(scores).hasSize(4);

        // The first deposit, seen alone, is unremarkable — exactly v0's blind spot.
        assertThat(scores.get(0).score()).isZero();
        assertThat(scores.get(0).reasons()).isEmpty();

        // By the fourth, the windowed sum/count reveal the structuring pattern.
        RiskScored last = scores.get(3);
        assertThat(last.score()).isEqualTo(70);
        assertThat(last.reasons()).anySatisfy(r -> assertThat(r).contains("STRUCTURING"));
    }

    @Test
    void manyQuickTransactions_triggerVelocity() {
        for (int i = 0; i < 6; i++) {
            send("ACC-VEL", "500.00", "CA", T0.plus(Duration.ofMinutes(i)));
        }

        List<RiskScored> scores = output.readValuesToList();
        assertThat(scores).hasSize(6);
        assertThat(scores.get(0).score()).isZero();

        RiskScored last = scores.get(5);
        assertThat(last.reasons()).anySatisfy(r -> assertThat(r).contains("VELOCITY"));
    }

    @Test
    void countryChangeInShortGap_triggersImpossibleTravel() {
        send("ACC-TRAVEL", "500.00", "CA", T0);
        send("ACC-TRAVEL", "500.00", "GB", T0.plus(Duration.ofMinutes(30)));

        List<RiskScored> scores = output.readValuesToList();
        assertThat(scores).hasSize(2);
        // First transaction has no prior to compare against.
        assertThat(scores.get(0).reasons()).noneSatisfy(r -> assertThat(r).contains("IMPOSSIBLE_TRAVEL"));
        // Second: CA → GB in 30 minutes.
        assertThat(scores.get(1).reasons())
                .anySatisfy(r -> assertThat(r).contains("IMPOSSIBLE_TRAVEL"));
    }
}
