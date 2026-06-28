package com.dolos.ingestion.service;

import com.dolos.events.Topics;
import com.dolos.events.TransactionReceived;
import com.dolos.ingestion.api.dto.IngestTransactionRequest;
import com.dolos.ingestion.domain.RawTransaction;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Reactive ingest pipeline: record the raw inbound transaction (R2DBC) then publish a
 * {@link TransactionReceived} event to Kafka. Persisting first means we never emit an event for
 * a transaction we failed to durably record.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final R2dbcEntityTemplate template;
    private final KafkaTemplate<String, Object> kafka;

    public IngestionService(R2dbcEntityTemplate template, KafkaTemplate<String, Object> kafka) {
        this.template = template;
        this.kafka = kafka;
    }

    /**
     * Ingests one transaction, returning the assigned transaction id once the raw record is
     * persisted and the event is published.
     */
    public Mono<UUID> ingest(IngestTransactionRequest request) {
        UUID transactionId = UUID.randomUUID();
        Instant receivedAt = Instant.now();
        RawTransaction raw = RawTransaction.received(transactionId, request, receivedAt);

        return template
                .insert(raw)
                .flatMap(saved -> publish(saved).thenReturn(saved.getId()))
                .doOnSuccess(id -> log.info("Ingested transaction {} for account {}", id, request.accountId()));
    }

    private Mono<Void> publish(RawTransaction raw) {
        TransactionReceived event =
                new TransactionReceived(
                        raw.getId(),
                        raw.getAccountId(),
                        raw.getCounterpartyAccountId(),
                        raw.getAmount(),
                        raw.getCurrency(),
                        raw.getDirection(),
                        raw.getDescription(),
                        raw.getCountry(),
                        raw.getCustomerId(),
                        raw.getDeviceId(),
                        raw.getOccurredAt(),
                        raw.getReceivedAt());
        // Key by account id for per-account partition ordering. send() returns a CompletableFuture;
        // adapt it to a Mono so the publish stays inside the reactive pipeline.
        return Mono.fromFuture(kafka.send(Topics.TRANSACTIONS_RECEIVED, raw.getAccountId(), event)).then();
    }
}
