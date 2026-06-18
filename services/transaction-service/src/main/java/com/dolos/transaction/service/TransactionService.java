package com.dolos.transaction.service;

import com.dolos.events.TransactionReceived;
import com.dolos.transaction.api.TransactionMapper;
import com.dolos.transaction.api.dto.CreateTransactionRequest;
import com.dolos.transaction.api.dto.TransactionResponse;
import com.dolos.transaction.domain.Direction;
import com.dolos.transaction.domain.TransactionEntity;
import com.dolos.transaction.repo.TransactionRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for persisting and retrieving transactions. */
@Service
public class TransactionService {

    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        TransactionEntity saved = repository.save(TransactionMapper.toEntity(request));
        return TransactionMapper.toResponse(saved);
    }

    /**
     * Persists a transaction that arrived as a {@link TransactionReceived} event. Idempotent: the
     * event's {@code transactionId} is the primary key, so a redelivery is a no-op rather than a
     * duplicate row.
     *
     * @return {@code true} if a new row was written, {@code false} if it already existed
     */
    @Transactional
    public boolean persistReceived(TransactionReceived event) {
        if (repository.existsById(event.transactionId())) {
            return false;
        }
        TransactionEntity entity =
                new TransactionEntity(
                        event.transactionId(),
                        event.accountId(),
                        event.counterpartyAccountId(),
                        event.amount(),
                        event.currency().toUpperCase(Locale.ROOT),
                        Direction.valueOf(event.direction()),
                        event.description(),
                        event.occurredAt(),
                        Instant.now());
        try {
            repository.save(entity);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            // A concurrent consumer (or redelivery racing the existsById check) already inserted
            // this id. The primary key held the line — treat it as the idempotent no-op it is.
            return false;
        }
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID id) {
        return repository
                .findById(id)
                .map(TransactionMapper::toResponse)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }
}
