package com.dolos.graph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for graph-service (Phase 2D–2E). Consumes {@code TransactionReceived} and builds the
 * fraud graph in Neo4j — {@code (:Account)-[:TRANSACTED]->(:Account)},
 * {@code (:Customer)-[:OWNS]->(:Account)}, {@code (:Account)-[:USED]->(:Device)} — exposing
 * {@code GET /api/graph/account/{id}/neighborhood} and (Phase 2E) detecting mule rings.
 */
@SpringBootApplication
public class GraphServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphServiceApplication.class, args);
    }
}
