package com.dolos.graph.messaging;

import com.dolos.events.TransactionReceived;
import com.dolos.events.Topics;
import com.dolos.graph.service.GraphProjectionService;
import com.dolos.graph.service.RingDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link TransactionReceived}, projects it into the Neo4j fraud graph, then checks whether
 * the just-updated account now closes a mule ring (Phase 2D–2E). Runs on a virtual thread (see
 * {@code KafkaConsumerConfig}); the MERGE and ring detection are both idempotent, so a redelivery is
 * harmless.
 */
@Component
public class TransactionReceivedListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionReceivedListener.class);

    private final GraphProjectionService projection;
    private final RingDetectionService ringDetection;
    private final KafkaTemplate<String, Object> kafka;

    public TransactionReceivedListener(
            GraphProjectionService projection,
            RingDetectionService ringDetection,
            KafkaTemplate<String, Object> kafka) {
        this.projection = projection;
        this.ringDetection = ringDetection;
        this.kafka = kafka;
    }

    @KafkaListener(topics = Topics.TRANSACTIONS_RECEIVED)
    public void onTransactionReceived(TransactionReceived event) {
        projection.project(event);
        detectRing(event.accountId());
    }

    /**
     * Ring detection failures must not block transaction ingestion (the projection already
     * committed), so they are caught and logged rather than rethrown — the next transaction on this
     * account, or a manual re-scan, will pick the ring up.
     */
    private void detectRing(String accountId) {
        try {
            ringDetection
                    .findNewRingFrom(accountId)
                    .ifPresent(
                            ring -> {
                                kafka.send(Topics.RINGS_DETECTED, ring.accounts().get(0), ring);
                                log.info(
                                        "Published RingDetected {} (score {}): {}",
                                        ring.ringId(),
                                        ring.score(),
                                        ring.pattern());
                            });
        } catch (RuntimeException e) {
            log.warn("Ring detection failed for account {}: {}", accountId, e.getMessage());
        }
    }
}
