package com.dolos.scoring.service;

import com.dolos.events.RiskScored;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A bounded, in-memory cache of recently produced scores, keyed by transaction id (Phase 2C). The
 * Kafka {@link RiskScored} event stays lightweight; full detail is fetched on demand over gRPC
 * ({@code GetScoreDetails}), which reads this cache. The topology populates it as it scores.
 *
 * <p>Bounded LRU (eldest-eviction) so memory stays flat under load — a miss simply means the detail
 * is no longer cached, which the gRPC layer reports as {@code found = false}.
 */
@Component
public class ScoreCache {

    private static final int MAX_ENTRIES = 10_000;

    private final Map<UUID, CachedScore> cache =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<UUID, CachedScore> eldest) {
                            return size() > MAX_ENTRIES;
                        }
                    });

    /** Records the detail of a scored transaction. */
    public void put(RiskScored scored) {
        cache.put(
                scored.transactionId(),
                new CachedScore(scored.accountId(), scored.score(), scored.reasons()));
    }

    /** The cached detail for a transaction, if still present. */
    public Optional<CachedScore> get(UUID transactionId) {
        return Optional.ofNullable(cache.get(transactionId));
    }

    /** The slice of a score worth serving over gRPC. */
    public record CachedScore(String accountId, int score, List<String> reasons) {}
}
