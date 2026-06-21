package com.dolos.alert.messaging;

import com.dolos.alert.service.AlertService;
import com.dolos.events.RiskScored;
import com.dolos.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link RiskScored} from the event backbone and hands it to {@link AlertService}, which
 * decides whether it crosses the alert threshold (Phase 1E). Runs on a virtual thread (see {@code
 * KafkaConsumerConfig}); alert creation is idempotent, so a redelivery is harmless.
 */
@Component
public class RiskScoredListener {

    private final AlertService service;

    public RiskScoredListener(AlertService service) {
        this.service = service;
    }

    @KafkaListener(topics = Topics.RISK_SCORED)
    public void onRiskScored(RiskScored event) {
        service.handle(event);
    }
}
