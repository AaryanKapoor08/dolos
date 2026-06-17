package com.dolos.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dolos.events.TransactionReceived;
import com.dolos.events.Topics;
import com.dolos.ingestion.api.dto.IngestTransactionRequest;
import com.dolos.ingestion.domain.RawTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock private R2dbcEntityTemplate template;

    @Mock private KafkaTemplate<String, Object> kafka;

    @InjectMocks private IngestionService service;

    @Test
    void ingest_persistsRawThenPublishesEvent() {
        var request =
                new IngestTransactionRequest(
                        "ACC-1",
                        "ACC-2",
                        new BigDecimal("12500.00"),
                        "CAD",
                        "CREDIT",
                        "structuring test",
                        Instant.parse("2026-06-16T20:00:00Z"));

        // Template echoes back whatever it is asked to insert.
        when(template.insert(any(RawTransaction.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(kafka.send(eq(Topics.TRANSACTIONS_RECEIVED), eq("ACC-1"), any(TransactionReceived.class)))
                .thenReturn(completedSend());

        StepVerifier.create(service.ingest(request))
                .assertNext(id -> assertThat(id).isNotNull())
                .verifyComplete();

        verify(template).insert(any(RawTransaction.class));

        var eventCaptor = ArgumentCaptor.forClass(TransactionReceived.class);
        verify(kafka).send(eq(Topics.TRANSACTIONS_RECEIVED), eq("ACC-1"), eventCaptor.capture());
        TransactionReceived published = eventCaptor.getValue();
        assertThat(published.accountId()).isEqualTo("ACC-1");
        assertThat(published.amount()).isEqualByComparingTo("12500.00");
        assertThat(published.currency()).isEqualTo("CAD");
        assertThat(published.direction()).isEqualTo("CREDIT");
        assertThat(published.transactionId()).isNotNull();
        assertThat(published.receivedAt()).isNotNull();
    }

    /**
     * A completed send future. The service ignores the SendResult (it only chains off completion),
     * so a null-valued completed future faithfully models a successful publish.
     */
    private static CompletableFuture<SendResult<String, Object>> completedSend() {
        return CompletableFuture.completedFuture(null);
    }
}
