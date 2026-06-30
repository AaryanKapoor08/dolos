package com.dolos.casework.integration.messaging;

import com.dolos.casework.integration.intake.CaseIntakeService;
import com.dolos.events.AlertRaised;
import com.dolos.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link AlertRaised} from the event backbone and hands it to {@link CaseIntakeService}, which
 * opens an investigation case for HIGH alerts (Phase 3E). Runs on a virtual thread (see {@code
 * KafkaConsumerConfig}); case opening is idempotent, so a redelivery is harmless.
 */
@Component
public class AlertRaisedListener {

    private final CaseIntakeService intake;

    public AlertRaisedListener(CaseIntakeService intake) {
        this.intake = intake;
    }

    @KafkaListener(topics = Topics.ALERTS_RAISED, containerFactory = "alertListenerContainerFactory")
    public void onAlertRaised(AlertRaised event) {
        intake.onAlert(event);
    }
}
