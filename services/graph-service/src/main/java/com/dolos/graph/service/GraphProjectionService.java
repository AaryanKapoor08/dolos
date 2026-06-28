package com.dolos.graph.service;

import com.dolos.events.TransactionReceived;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.exceptions.Neo4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

/**
 * Projects each {@link TransactionReceived} into the Neo4j fraud graph (Phase 2D).
 *
 * <p>One idempotent Cypher statement MERGEs:
 *
 * <ul>
 *   <li>the subject {@code (:Account)} and, when present, its {@code (:Customer)-[:OWNS]->} owner
 *       and the {@code (:Account)-[:USED]->(:Device)} it was made from;
 *   <li>a directed {@code (:Account)-[:TRANSACTED {txId, amount, ts}]->(:Account)} edge oriented by
 *       <b>money flow</b> — for a DEBIT money leaves the subject account toward the counterparty, for
 *       a CREDIT it arrives from the counterparty. Orienting by flow is what lets Phase 2E find
 *       directed cash-out cycles (A&rarr;B&rarr;C&rarr;A).
 * </ul>
 *
 * <p>MERGE keyed on {@code txId} makes the write idempotent: a Kafka redelivery re-sets the same
 * edge rather than duplicating it. Uses {@link Neo4jClient} (Spring Data Neo4j) for parameterised
 * Cypher — MERGE-with-relationship-properties is awkward to express through OGM mapping.
 */
@Service
public class GraphProjectionService {

    private static final Logger log = LoggerFactory.getLogger(GraphProjectionService.class);

    // Single idempotent upsert. FOREACH-over-a-(possibly-empty)-list is the Cypher idiom for
    // "do this only when the optional value is present" without branching the statement.
    private static final String MERGE_CYPHER =
            """
            MERGE (a:Account {id: $accountId})
            FOREACH (cid IN $customers |
              MERGE (c:Customer {id: cid})
              MERGE (c)-[:OWNS]->(a))
            FOREACH (did IN $devices |
              MERGE (d:Device {id: did})
              MERGE (a)-[:USED]->(d))
            FOREACH (_ IN $edge |
              MERGE (src:Account {id: $fromId})
              MERGE (dst:Account {id: $toId})
              MERGE (src)-[t:TRANSACTED {txId: $txId}]->(dst)
                SET t.amount = $amount, t.ts = $ts, t.currency = $currency)
            """;

    private final Neo4jClient neo4j;

    public GraphProjectionService(Neo4jClient neo4j) {
        this.neo4j = neo4j;
    }

    public void project(TransactionReceived event) {
        boolean credit = "CREDIT".equalsIgnoreCase(event.direction());
        String counterparty = event.counterpartyAccountId();
        // Orient the edge by money flow: DEBIT = subject -> counterparty, CREDIT = counterparty -> subject.
        String fromId = credit ? counterparty : event.accountId();
        String toId = credit ? event.accountId() : counterparty;
        boolean hasEdge = counterparty != null && !counterparty.isBlank();

        Map<String, Object> params = new java.util.HashMap<>();
        params.put("accountId", event.accountId());
        params.put("customers", optionalList(event.customerId()));
        params.put("devices", optionalList(event.deviceId()));
        params.put("edge", hasEdge ? List.of(1) : List.of());
        params.put("fromId", fromId);
        params.put("toId", toId);
        params.put("txId", event.transactionId().toString());
        params.put("amount", event.amount() == null ? null : event.amount().doubleValue());
        params.put("ts", event.occurredAt().toEpochMilli());
        params.put("currency", event.currency());

        try {
            neo4j.query(MERGE_CYPHER).bindAll(params).run();
            log.debug(
                    "Projected transaction {} into graph (account {}{})",
                    event.transactionId(),
                    event.accountId(),
                    hasEdge ? " -> " + toId : "");
        } catch (Neo4jException e) {
            // Let the listener's error handler retry transient Bolt failures.
            log.warn("Failed to project transaction {} into graph: {}", event.transactionId(), e.getMessage());
            throw e;
        }
    }

    private static List<String> optionalList(String value) {
        return (value == null || value.isBlank()) ? List.of() : List.of(value);
    }
}
