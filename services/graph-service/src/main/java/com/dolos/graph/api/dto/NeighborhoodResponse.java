package com.dolos.graph.api.dto;

import java.util.List;

/**
 * The one-hop neighborhood of an account in the fraud graph (Phase 2D): who owns it, which devices
 * it used, and its directed transaction edges (money-flow oriented). Shaped for the UI / a quick
 * Cypher-free look at an account without touching Neo4j directly.
 *
 * @param accountId the subject account
 * @param inRing    whether this account is a member of any detected mule/cash-out ring (Phase 2E)
 * @param rings     ids of the rings this account belongs to ({@code IN_RING} markers; empty if none)
 * @param owners    customer ids that {@code OWN} this account
 * @param devices   device ids this account {@code USED}
 * @param outgoing  TRANSACTED edges where money left this account (this -> counterparty)
 * @param incoming  TRANSACTED edges where money arrived at this account (counterparty -> this)
 */
public record NeighborhoodResponse(
        String accountId,
        boolean inRing,
        List<String> rings,
        List<String> owners,
        List<String> devices,
        List<EdgeView> outgoing,
        List<EdgeView> incoming) {}
