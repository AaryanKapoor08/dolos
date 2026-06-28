package com.dolos.graph.service;

import com.dolos.events.RingDetected;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

/**
 * Detects mule/cash-out rings in the fraud graph (Phase 2E): a directed cycle of money-flow
 * {@code TRANSACTED} edges that returns to its origin (A&rarr;B&rarr;C&rarr;&hellip;&rarr;A). Uses a
 * bounded variable-length Cypher path {@code [:TRANSACTED*min..max]} — the pure-Cypher alternative to
 * Neo4j GDS community detection, sufficient for the layered cash-out pattern and zero extra plugins.
 *
 * <p>Detection is made <b>idempotent</b> by a rotation-invariant {@code ringId} (the sorted set of
 * member accounts) and a {@code MERGE (:Ring {id})} marker: the first detection of a given ring
 * creates the marker and returns a {@link RingDetected}; subsequent detections of the same ring
 * (from any starting account, or on Kafka redelivery) match the existing marker and return empty, so
 * the ring is reported exactly once. Publishing the event is left to the caller.
 */
@Service
public class RingDetectionService {

    private static final Logger log = LoggerFactory.getLogger(RingDetectionService.class);

    private final Neo4jClient neo4j;
    private final int score;
    private final String cycleCypher;

    public RingDetectionService(
            Neo4jClient neo4j,
            @Value("${dolos.graph.ring.min-length}") int minLength,
            @Value("${dolos.graph.ring.max-length}") int maxLength,
            @Value("${dolos.graph.ring.score}") int score) {
        this.neo4j = neo4j;
        this.score = score;
        // Variable-length bounds can't be bound as Cypher parameters, so inline the (trusted, config)
        // ints. Find the shortest cycle through the start account first.
        this.cycleCypher =
                "MATCH p=(a:Account {id: $id})-[:TRANSACTED*"
                        + minLength
                        + ".."
                        + maxLength
                        + "]->(a) "
                        + "RETURN [n IN nodes(p) | n.id] AS ids, length(p) AS hops "
                        + "ORDER BY hops ASC LIMIT 1";
    }

    /**
     * Looks for a ring through {@code accountId}. Returns the ring only the first time it is seen
     * (idempotent on {@code ringId}); empty if there is no cycle or the ring was already reported.
     */
    public Optional<RingDetected> findNewRingFrom(String accountId) {
        Optional<RingPath> cycle =
                neo4j.query(cycleCypher)
                        .bind(accountId).to("id")
                        .fetchAs(RingPath.class)
                        .mappedBy((t, r) -> new RingPath(r.get("ids").asList(v -> v.asString())))
                        .first();

        if (cycle.isEmpty()) {
            return Optional.empty();
        }

        // nodes(p) includes the start node twice (open + close); the member accounts are all but the
        // repeated closing node, in money-flow order.
        List<String> ids = cycle.get().ids();
        List<String> accounts = new ArrayList<>(ids.subList(0, ids.size() - 1));
        int hops = ids.size() - 1;
        String ringId = canonicalRingId(accounts);
        String pattern = String.join(" -> ", ids); // e.g. "A -> B -> C -> A"
        Instant detectedAt = Instant.now();

        boolean isNew =
                Boolean.TRUE.equals(
                        neo4j.query(
                                        """
                                        MERGE (r:Ring {id: $ringId})
                                        ON CREATE SET r.createdAt = $ts, r.score = $score,
                                                      r.hops = $hops, r.pattern = $pattern, r.created = true
                                        ON MATCH SET r.created = false
                                        RETURN r.created AS created
                                        """)
                                .bindAll(
                                        Map.of(
                                                "ringId", ringId,
                                                "ts", detectedAt.toEpochMilli(),
                                                "score", score,
                                                "hops", hops,
                                                "pattern", pattern))
                                .fetchAs(Boolean.class)
                                .mappedBy((t, r) -> r.get("created").asBoolean())
                                .one()
                                .orElse(false));

        if (!isNew) {
            log.debug("Ring {} already reported — idempotent skip", ringId);
            return Optional.empty();
        }

        // Link the member accounts to the ring marker so the cluster is visible in the Neo4j Browser.
        neo4j.query(
                        "MATCH (r:Ring {id: $ringId}) UNWIND $accounts AS aid "
                                + "MATCH (a:Account {id: aid}) MERGE (a)-[:IN_RING]->(r)")
                .bindAll(Map.of("ringId", ringId, "accounts", accounts))
                .run();

        RingDetected ring = new RingDetected(ringId, accounts, score, pattern, hops, detectedAt);
        log.info("Detected ring {} ({} hops): {}", ringId, hops, pattern);
        return Optional.of(ring);
    }

    /** The node ids of a detected cycle, in path order (start node appears first and last). */
    private record RingPath(List<String> ids) {}

    /** Rotation-invariant id: the sorted set of member accounts, so any start node yields the same id. */
    static String canonicalRingId(List<String> accounts) {
        List<String> sorted = new ArrayList<>(accounts);
        sorted.sort(String::compareTo);
        return "RING-" + String.join("-", sorted);
    }
}
