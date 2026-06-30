package com.dolos.casework.integration.messaging;

import com.dolos.events.CaseClosed;
import com.dolos.events.CaseEscalated;
import com.dolos.events.CaseOpened;
import com.dolos.events.CaseReportFiled;
import com.dolos.events.Topics;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The outbound half of the Modulith transactional outbox (Phase 3E): each {@link ApplicationModuleListener}
 * runs after the publishing transaction commits and sends the case event to Kafka. Spring Modulith
 * records the publication before invoking the listener and marks it complete only when the listener
 * returns normally — so a failed send (broker down) leaves the publication incomplete and it is
 * resubmitted on restart. That yields at-least-once delivery; downstream consumers stay idempotent.
 *
 * <p>The send is awaited (and rethrows on failure) precisely so a broker blip is recorded as an
 * incomplete publication rather than silently lost.
 */
@Component
public class CaseEventPublisher {

    private final KafkaTemplate<String, Object> kafka;

    public CaseEventPublisher(KafkaTemplate<String, Object> caseKafkaTemplate) {
        this.kafka = caseKafkaTemplate;
    }

    @ApplicationModuleListener
    void on(CaseOpened event) {
        publish(Topics.CASES_OPENED, event.caseId(), event);
    }

    @ApplicationModuleListener
    void on(CaseEscalated event) {
        publish(Topics.CASES_ESCALATED, event.caseId(), event);
    }

    @ApplicationModuleListener
    void on(CaseReportFiled event) {
        publish(Topics.CASES_REPORTED, event.caseId(), event);
    }

    @ApplicationModuleListener
    void on(CaseClosed event) {
        publish(Topics.CASES_CLOSED, event.caseId(), event);
    }

    private void publish(String topic, UUID caseId, Object event) {
        try {
            // Block on the ack so a broker failure surfaces as an exception (-> publication stays
            // incomplete and is resubmitted), rather than a silently dropped send. The wait exceeds the
            // producer's own max-block + delivery timeout, so a blip resolves as a failed future.
            kafka.send(topic, caseId.toString(), event).get(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted publishing " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("failed to publish " + topic + " for case " + caseId, e);
        }
    }
}
