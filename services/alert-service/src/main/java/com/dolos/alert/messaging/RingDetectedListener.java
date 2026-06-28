package com.dolos.alert.messaging;

import com.dolos.alert.service.AlertService;
import com.dolos.events.RingDetected;
import com.dolos.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link RingDetected} from graph-service (Phase 2E) and hands it to {@link AlertService},
 * which raises a HIGH-severity ring alert. Uses its own container factory (typed to {@code
 * RingDetected}); runs on a virtual thread. Ring alerts are idempotent on the ringId, so a
 * redelivery is harmless.
 */
@Component
public class RingDetectedListener {

    private final AlertService service;

    public RingDetectedListener(AlertService service) {
        this.service = service;
    }

    @KafkaListener(topics = Topics.RINGS_DETECTED, containerFactory = "ringKafkaListenerContainerFactory")
    public void onRingDetected(RingDetected event) {
        service.handleRing(event);
    }
}
