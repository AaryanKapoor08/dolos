package com.dolos.legacy.route;

import com.dolos.events.TransactionReceived;
import com.dolos.legacy.feed.LegacyTransactionRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConstants;
import org.springframework.stereotype.Component;

/**
 * The message-translator step: turn a decoded {@link LegacyTransactionRecord} into the platform's
 * canonical {@link TransactionReceived} event and prepare it for the Kafka producer. Mirrors the
 * ingestion-service wire contract exactly — a fresh {@code transactionId}, {@code java.time} values as
 * ISO-8601 strings, no Jackson type headers — so downstream consumers can't tell this event came from
 * the legacy feed rather than the reactive edge. The subject account becomes the Kafka message key
 * (via {@link KafkaConstants#KEY}) so an account keeps per-partition ordering.
 *
 * <p>The legacy feed carries no customer or device id, so those canonical fields are {@code null}
 * (graph-service simply skips the OWNS/USED edges it would otherwise MERGE).
 */
@Component
public class CanonicalTransactionTranslator implements Processor {

    // A dedicated mapper matching ingestion-service's: ISO-8601 dates, no numeric epochs, no type info.
    private final ObjectMapper mapper =
            JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .build();

    @Override
    public void process(Exchange exchange) throws Exception {
        LegacyTransactionRecord record = exchange.getIn().getBody(LegacyTransactionRecord.class);

        TransactionReceived event =
                new TransactionReceived(
                        UUID.randomUUID(),
                        record.accountId(),
                        record.counterpartyAccountId(),
                        record.amount(),
                        record.currency(),
                        record.direction(),
                        record.description(),
                        record.country(),
                        null, // customerId — not carried by the legacy feed
                        null, // deviceId  — not carried by the legacy feed
                        record.occurredAt(),
                        Instant.now());

        exchange.getIn().setHeader(KafkaConstants.KEY, record.accountId());
        exchange.getIn().setHeader(LegacyHeaders.ACCOUNT_ID, record.accountId());
        exchange.getIn().setBody(mapper.writeValueAsString(event));
    }
}
