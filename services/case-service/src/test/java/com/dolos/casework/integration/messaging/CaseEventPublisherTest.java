package com.dolos.casework.integration.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dolos.events.CaseClosed;
import com.dolos.events.CaseEscalated;
import com.dolos.events.CaseOpened;
import com.dolos.events.CaseReportFiled;
import com.dolos.events.Topics;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Phase 3E — the outbox-completion contract that makes case events publish exactly once. The Modulith
 * registry marks a publication complete only when the listener returns normally, and resubmits it on
 * restart otherwise. So the two halves verified here are: a successful send routes the event to its
 * topic keyed by case id (deliver once), and a failed send is rethrown (the publication stays
 * incomplete and is resubmitted rather than silently lost). The end-to-end exactly-once-across-a-broker
 * -blip behaviour is verified in Docker.
 */
class CaseEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);

    private final CaseEventPublisher publisher = new CaseEventPublisher(kafka);

    private void sendSucceeds() {
        CompletableFuture<SendResult<String, Object>> ok = CompletableFuture.completedFuture(null);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(ok);
    }

    private void sendFails() {
        CompletableFuture<SendResult<String, Object>> failed =
                CompletableFuture.failedFuture(new RuntimeException("broker down"));
        when(kafka.send(anyString(), anyString(), any())).thenReturn(failed);
    }

    @Test
    void routesEachEventTypeToItsTopicKeyedByCaseId() {
        sendSucceeds();
        UUID caseId = UUID.randomUUID();
        Instant at = Instant.parse("2026-06-30T00:00:00Z");

        CaseOpened opened = new CaseOpened(caseId, UUID.randomUUID(), "ACC-1", 90, "sys", at);
        CaseEscalated escalated = new CaseEscalated(caseId, "ring", "senior", at);
        CaseReportFiled reported = new CaseReportFiled(caseId, "SAR-1", "senior", at);
        CaseClosed closed = new CaseClosed(caseId, "filed", "senior", at);

        publisher.on(opened);
        publisher.on(escalated);
        publisher.on(reported);
        publisher.on(closed);

        String key = caseId.toString();
        verify(kafka).send(Topics.CASES_OPENED, key, opened);
        verify(kafka).send(Topics.CASES_ESCALATED, key, escalated);
        verify(kafka).send(Topics.CASES_REPORTED, key, reported);
        verify(kafka).send(Topics.CASES_CLOSED, key, closed);
    }

    @Test
    void rethrowsWhenSendFails_soThePublicationStaysIncomplete() {
        sendFails();
        CaseOpened opened =
                new CaseOpened(
                        UUID.randomUUID(), UUID.randomUUID(), "ACC-2", 88, "sys", Instant.now());

        // A thrown listener -> Modulith does NOT complete the publication -> resubmitted on restart.
        assertThatThrownBy(() -> publisher.on(opened)).isInstanceOf(IllegalStateException.class);
    }
}
