package com.dolos.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dolos.alert.domain.AlertEntity;
import com.dolos.alert.grpc.ScoreDetailClient;
import com.dolos.alert.grpc.ScoreDetailView;
import com.dolos.alert.repo.AlertRepository;
import com.dolos.events.AlertRaised;
import com.dolos.events.RiskScored;
import com.dolos.events.Topics;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final int THRESHOLD = 60;

    @Mock private AlertRepository repository;
    @Mock private ScoreDetailClient scoreDetailClient;

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = (KafkaTemplate<String, Object>) org.mockito.Mockito.mock(KafkaTemplate.class);

    private AlertService service() {
        return new AlertService(repository, kafka, scoreDetailClient, THRESHOLD);
    }

    private static RiskScored scored(int score) {
        return new RiskScored(
                UUID.randomUUID(),
                "ACC-1",
                score,
                List.of("LARGE_AMOUNT: amount 15000 is at/above the $10k reporting threshold"),
                Instant.parse("2026-02-02T00:00:00Z"));
    }

    @Test
    void atOrAboveThreshold_newTransaction_persistsAndPublishes() {
        RiskScored event = scored(60);
        when(repository.existsByTransactionId(event.transactionId())).thenReturn(false);
        when(repository.save(any(AlertEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(scoreDetailClient.getScoreDetails(event.transactionId()))
                .thenReturn(new ScoreDetailView(true, "1 rule(s) fired"));

        service().handle(event);

        ArgumentCaptor<AlertEntity> saved = ArgumentCaptor.forClass(AlertEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTransactionId()).isEqualTo(event.transactionId());
        assertThat(saved.getValue().getScore()).isEqualTo(60);
        assertThat(saved.getValue().getDetail()).isEqualTo("1 rule(s) fired");

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(Topics.ALERTS_RAISED), eq("ACC-1"), published.capture());
        assertThat(published.getValue()).isInstanceOf(AlertRaised.class);
        AlertRaised raised = (AlertRaised) published.getValue();
        assertThat(raised.transactionId()).isEqualTo(event.transactionId());
        assertThat(raised.score()).isEqualTo(60);
        assertThat(raised.reasons()).first().asString().contains("LARGE_AMOUNT");
    }

    @Test
    void belowThreshold_doesNothing() {
        RiskScored event = scored(50);

        service().handle(event);

        verify(repository, never()).save(any());
        verify(kafka, never()).send(any(), any(), any());
    }

    @Test
    void duplicateTransaction_isIdempotentNoOp() {
        RiskScored event = scored(80);
        when(repository.existsByTransactionId(event.transactionId())).thenReturn(true);

        service().handle(event);

        verify(repository, never()).save(any());
        verify(kafka, never()).send(any(), any(), any());
    }
}
