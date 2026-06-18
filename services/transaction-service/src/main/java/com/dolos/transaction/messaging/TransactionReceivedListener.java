package com.dolos.transaction.messaging;

import com.dolos.events.TransactionReceived;
import com.dolos.events.Topics;
import com.dolos.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link TransactionReceived} from the event backbone and persists the canonical
 * transaction (Phase 1C). This is the event-driven path into the store of record — distinct from
 * the direct {@code POST /api/transactions} REST path, which remains for ad-hoc/manual entry.
 *
 * <p>Keyed by account id at the producer, so a given account's events stay partition-ordered and
 * a single consumer thread sees them in order. Persistence is idempotent (dedupe on transaction id),
 * so a redelivery after a crash or rebalance is harmless.
 */
@Component
public class TransactionReceivedListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionReceivedListener.class);

    private final TransactionService service;

    public TransactionReceivedListener(TransactionService service) {
        this.service = service;
    }

    @KafkaListener(topics = Topics.TRANSACTIONS_RECEIVED)
    public void onTransactionReceived(TransactionReceived event) {
        boolean persisted = service.persistReceived(event);
        if (persisted) {
            log.info(
                    "Persisted canonical transaction {} for account {}",
                    event.transactionId(),
                    event.accountId());
        } else {
            log.debug(
                    "Skipped already-persisted transaction {} (idempotent redelivery)",
                    event.transactionId());
        }
    }
}
