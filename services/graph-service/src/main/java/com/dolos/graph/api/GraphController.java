package com.dolos.graph.api;

import com.dolos.graph.api.dto.NeighborhoodResponse;
import com.dolos.graph.service.GraphQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API over the fraud graph (Phase 2D). {@code GET /api/graph/account/{id}/neighborhood} returns
 * the account's owners, devices, and directed transaction edges — a Cypher-free window an analyst (or
 * the Phase 5 UI) can hit without touching Neo4j.
 */
@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphQueryService query;

    public GraphController(GraphQueryService query) {
        this.query = query;
    }

    @GetMapping("/account/{id}/neighborhood")
    public NeighborhoodResponse neighborhood(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("account id must not be blank");
        }
        return query.neighborhood(id);
    }
}
