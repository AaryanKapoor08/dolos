package com.dolos.transaction.api;

import com.dolos.common.AccountId;
import com.dolos.common.Money;
import com.dolos.transaction.api.dto.CreateTransactionRequest;
import com.dolos.transaction.api.dto.TransactionResponse;
import com.dolos.transaction.domain.TransactionEntity;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Pure mapping between the transaction JPA entity and its API DTOs. */
public final class TransactionMapper {

    private TransactionMapper() {}

    /** Build a new entity from a create request (assigns id + createdAt). */
    public static TransactionEntity toEntity(CreateTransactionRequest req) {
        return new TransactionEntity(
                UUID.randomUUID(),
                req.accountId(),
                req.counterpartyAccountId(),
                req.amount(),
                req.currency().toUpperCase(Locale.ROOT),
                req.direction(),
                req.description(),
                req.occurredAt(),
                Instant.now());
    }

    public static TransactionResponse toResponse(TransactionEntity e) {
        AccountId counterparty =
                e.getCounterpartyAccountId() == null ? null : AccountId.of(e.getCounterpartyAccountId());
        return new TransactionResponse(
                e.getId(),
                AccountId.of(e.getAccountId()),
                counterparty,
                Money.of(e.getAmount(), e.getCurrency()),
                e.getDirection(),
                e.getDescription(),
                e.getOccurredAt(),
                e.getCreatedAt());
    }
}
