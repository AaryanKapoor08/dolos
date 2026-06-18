package com.dolos.scoring.messaging;

import com.dolos.events.RiskScored;
import com.dolos.events.Topics;
import com.dolos.events.TransactionReceived;
import com.dolos.scoring.service.RiskScoringEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link TransactionReceived}, scores it with {@link RiskScoringEngine}, and publishes a
 * {@link RiskScored} event (Phase 1D). Keyed by account id so a given account's scores keep
 * partition order, mirroring the upstream transaction key.
 */
@Component
public class TransactionReceivedListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionReceivedListener.class);

    private final RiskScoringEngine engine;
    private final KafkaTemplate<String, Object> kafka;

    public TransactionReceivedListener(RiskScoringEngine engine, KafkaTemplate<String, Object> kafka) {
        this.engine = engine;
        this.kafka = kafka;
    }

    @KafkaListener(topics = Topics.TRANSACTIONS_RECEIVED)
    public void onTransactionReceived(TransactionReceived event) {
        RiskScored scored = engine.score(event);
        kafka.send(Topics.RISK_SCORED, scored.accountId(), scored);
        log.info(
                "Scored transaction {} → score {} {}",
                scored.transactionId(),
                scored.score(),
                scored.reasons());
    }
}
