package com.dolos.transaction.api.dto;

import com.dolos.transaction.domain.Direction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Inbound payload for creating a transaction. Validated before reaching the service. */
public record CreateTransactionRequest(
        @NotBlank String accountId,
        String counterpartyAccountId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull Direction direction,
        String description,
        @NotNull Instant occurredAt) {}
