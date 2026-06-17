package com.dolos.ingestion.api.dto;

import java.util.UUID;

/**
 * Response body acknowledging that a transaction was accepted for processing. The id is the
 * assigned {@code transactionId}, by which the transaction can later be queried in
 * transaction-service or correlated across events.
 */
public record IngestAccepted(UUID transactionId, String status) {

    public static IngestAccepted of(UUID transactionId) {
        return new IngestAccepted(transactionId, "RECEIVED");
    }
}
