package com.dolos.alert.grpc;

/**
 * The alert-service's own view of a score detail fetched over gRPC (Phase 2C) — deliberately not the
 * generated proto type, so the proto stays an edge concern. {@code found} distinguishes a real detail
 * from the resilience fallback used when scoring-service is unreachable.
 *
 * @param found   whether scoring-service had a detail for the transaction
 * @param summary short human-readable detail (or "details unavailable" on fallback)
 */
public record ScoreDetailView(boolean found, String summary) {

    /** The fallback view produced when the scoring gRPC call fails or the breaker is open. */
    public static ScoreDetailView unavailable() {
        return new ScoreDetailView(false, "details unavailable");
    }
}
