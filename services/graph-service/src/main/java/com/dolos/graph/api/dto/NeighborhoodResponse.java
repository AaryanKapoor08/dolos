package com.dolos.graph.api.dto;

import java.util.List;

/**
 * The one-hop neighborhood of an account in the fraud graph (Phase 2D): who owns it, which devices
 * it used, and its directed transaction edges (money-flow oriented). Shaped for the UI / a quick
 * Cypher-free look at an account without touching Neo4j directly.
 *
 * @param accountId the subject account
 * @param owners    customer ids that {@code OWN} this account
 * @param devices   device ids this account {@code USED}
 * @param outgoing  TRANSACTED edges where money left this account (this -> counterparty)
 * @param incoming  TRANSACTED edges where money arrived at this account (counterparty -> this)
 */
public record NeighborhoodResponse(
        String accountId,
        List<String> owners,
        List<String> devices,
        List<EdgeView> outgoing,
        List<EdgeView> incoming) {}
