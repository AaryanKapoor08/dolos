package com.dolos.transaction.api.dto;

import com.dolos.common.AccountId;
import com.dolos.common.Money;
import com.dolos.transaction.domain.Direction;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound representation of a transaction. Uses the shared {@link Money} / {@link AccountId} value
 * objects from dolos-common so the API never leaks the JPA entity.
 */
public record TransactionResponse(
        UUID id,
        AccountId account,
        AccountId counterparty,
        Money amount,
        Direction direction,
        String description,
        Instant occurredAt,
        Instant createdAt) {}
