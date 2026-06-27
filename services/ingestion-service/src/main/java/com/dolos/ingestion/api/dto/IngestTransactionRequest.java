package com.dolos.ingestion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Inbound payload accepted by ingestion-service. Validated before the reactive pipeline runs.
 * Unlike transaction-service's create request there is no id — ingestion assigns one.
 *
 * <p>{@code direction} is a plain String (constrained to DEBIT/CREDIT) to match the
 * {@code dolos-events} wire contract.
 */
public record IngestTransactionRequest(
        @NotBlank String accountId,
        String counterpartyAccountId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotBlank @Pattern(regexp = "DEBIT|CREDIT", message = "must be DEBIT or CREDIT") String direction,
        String description,
        @Pattern(regexp = "[A-Z]{2}", message = "must be an ISO-3166 alpha-2 country code") String country,
        @NotNull Instant occurredAt) {}
