package com.dolos.graph.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.dolos.events.RingDetected;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Phase 2E Testcontainers test: drives {@link RingDetectionService} against a real Neo4j, seeding a
 * known A&rarr;B&rarr;C&rarr;D&rarr;A chain and asserting the bounded variable-length Cypher detects
 * it — and that detection is idempotent (the same ring, found from any starting account, is reported
 * exactly once).
 *
 * <p>Uses a plain Bolt {@link Driver} + {@link Neo4jClient} rather than a Spring context, so the test
 * exercises only the graph logic (no Kafka). The container is started manually after a
 * Docker-availability assumption, so on a machine where Testcontainers cannot reach a Docker daemon
 * the whole class is skipped rather than failing the build; CI (Linux Docker) runs it for real.
 */
class RingDetectionNeo4jIT {

    private static final String PASSWORD = "testpassword";

    private static final Neo4jContainer<?> NEO4J =
            new Neo4jContainer<>(DockerImageName.parse("neo4j:5.26-community"))
                    .withAdminPassword(PASSWORD);

    private static boolean started;
    private static Driver driver;
    private static Neo4jClient client;

    private RingDetectionService service;

    @BeforeAll
    static void startNeo4j() {
        assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not reachable by Testcontainers; skipping the Neo4j ring test");
        NEO4J.start();
        started = true;
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.basic("neo4j", PASSWORD));
        client = Neo4jClient.create(driver);
    }

    @AfterAll
    static void stopNeo4j() {
        if (driver != null) {
            driver.close();
        }
        if (started) {
            NEO4J.stop();
        }
    }

    @BeforeEach
    void freshGraph() {
        client.query("MATCH (n) DETACH DELETE n").run();
        // Same bounds as the service default (application.yml): cycles of 2..6 hops, ring score 85.
        service = new RingDetectionService(client, 2, 6, 85);
    }

    @Test
    void seededChain_isDetectedAsARing_andIsIdempotent() {
        seedChain("A", "B", "C", "D"); // A -> B -> C -> D -> A

        Optional<RingDetected> ring = service.findNewRingFrom("A");

        assertThat(ring).isPresent();
        assertThat(ring.get().accounts()).containsExactlyInAnyOrder("A", "B", "C", "D");
        assertThat(ring.get().hops()).isEqualTo(4);
        assertThat(ring.get().score()).isEqualTo(85);
        assertThat(ring.get().ringId()).isEqualTo("RING-A-B-C-D");
        assertThat(ring.get().pattern()).isEqualTo("A -> B -> C -> D -> A");

        // Idempotent: the same ring, detected again from a different member account, is not re-reported.
        assertThat(service.findNewRingFrom("C")).isEmpty();
    }

    @Test
    void openChainWithNoCycle_isNotARing() {
        // X -> Y -> Z, no edge back to X.
        client.query(
                        "CREATE (x:Account {id:'X'}), (y:Account {id:'Y'}), (z:Account {id:'Z'}),"
                                + " (x)-[:TRANSACTED]->(y), (y)-[:TRANSACTED]->(z)")
                .run();

        assertThat(service.findNewRingFrom("X")).isEmpty();
    }

    /** Seeds a directed TRANSACTED cycle through the given accounts (last -> first closes the ring). */
    private void seedChain(String... accounts) {
        StringBuilder cypher = new StringBuilder();
        for (String id : accounts) {
            cypher.append("MERGE (").append(id).append(":Account {id:'").append(id).append("'}) ");
        }
        for (int i = 0; i < accounts.length; i++) {
            String from = accounts[i];
            String to = accounts[(i + 1) % accounts.length];
            cypher.append("MERGE (")
                    .append(from)
                    .append(")-[:TRANSACTED]->(")
                    .append(to)
                    .append(") ");
        }
        client.query(cypher.toString().trim()).run();
    }
}
