package com.dolos.graph.service;

import com.dolos.graph.api.dto.EdgeView;
import com.dolos.graph.api.dto.NeighborhoodResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

/**
 * Read side of the fraud graph (Phase 2D): returns an account's one-hop neighborhood by running
 * focused Cypher through {@link Neo4jClient}. Returns empty collections (not 404) for an account
 * with no graph footprint yet, so callers can treat "unknown" and "isolated" uniformly.
 */
@Service
public class GraphQueryService {

    private final Neo4jClient neo4j;

    public GraphQueryService(Neo4jClient neo4j) {
        this.neo4j = neo4j;
    }

    public NeighborhoodResponse neighborhood(String accountId) {
        List<String> owners =
                new ArrayList<>(
                        neo4j.query("MATCH (c:Customer)-[:OWNS]->(:Account {id: $id}) RETURN c.id AS id")
                                .bind(accountId).to("id")
                                .fetchAs(String.class)
                                .mappedBy((t, r) -> r.get("id").asString())
                                .all());

        List<String> devices =
                new ArrayList<>(
                        neo4j.query("MATCH (:Account {id: $id})-[:USED]->(d:Device) RETURN d.id AS id")
                                .bind(accountId).to("id")
                                .fetchAs(String.class)
                                .mappedBy((t, r) -> r.get("id").asString())
                                .all());

        List<EdgeView> outgoing =
                edges(
                        "MATCH (:Account {id: $id})-[t:TRANSACTED]->(b:Account) "
                                + "RETURN b.id AS counterparty, t.amount AS amount, t.ts AS ts ORDER BY t.ts",
                        accountId);

        List<EdgeView> incoming =
                edges(
                        "MATCH (:Account {id: $id})<-[t:TRANSACTED]-(b:Account) "
                                + "RETURN b.id AS counterparty, t.amount AS amount, t.ts AS ts ORDER BY t.ts",
                        accountId);

        // Ring membership comes straight from the :IN_RING markers written by ring detection (Phase 2E),
        // so the copilot can answer "is this account in a ring?" without re-running cycle detection.
        List<String> rings =
                new ArrayList<>(
                        neo4j.query("MATCH (:Account {id: $id})-[:IN_RING]->(r:Ring) RETURN r.id AS id")
                                .bind(accountId).to("id")
                                .fetchAs(String.class)
                                .mappedBy((t, r) -> r.get("id").asString())
                                .all());

        return new NeighborhoodResponse(
                accountId, !rings.isEmpty(), rings, owners, devices, outgoing, incoming);
    }

    private List<EdgeView> edges(String cypher, String accountId) {
        return new ArrayList<>(
                neo4j.query(cypher)
                        .bind(accountId).to("id")
                        .fetchAs(EdgeView.class)
                        .mappedBy(
                                (t, r) ->
                                        new EdgeView(
                                                r.get("counterparty").asString(),
                                                r.get("amount").isNull()
                                                        ? null
                                                        : BigDecimal.valueOf(r.get("amount").asDouble()),
                                                Instant.ofEpochMilli(r.get("ts").asLong())))
                        .all());
    }
}
