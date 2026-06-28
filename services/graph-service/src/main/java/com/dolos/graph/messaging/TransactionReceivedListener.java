package com.dolos.graph.messaging;

import com.dolos.events.TransactionReceived;
import com.dolos.events.Topics;
import com.dolos.graph.service.GraphProjectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link TransactionReceived} from the event backbone and projects it into the Neo4j fraud
 * graph (Phase 2D). Runs on a virtual thread (see {@code KafkaConsumerConfig}); the MERGE is
 * idempotent, so a redelivery is harmless.
 */
@Component
public class TransactionReceivedListener {

    private final GraphProjectionService projection;

    public TransactionReceivedListener(GraphProjectionService projection) {
        this.projection = projection;
    }

    @KafkaListener(topics = Topics.TRANSACTIONS_RECEIVED)
    public void onTransactionReceived(TransactionReceived event) {
        projection.project(event);
    }
}
