package com.dolos.transaction.service;

import com.dolos.transaction.api.TransactionMapper;
import com.dolos.transaction.api.dto.CreateTransactionRequest;
import com.dolos.transaction.api.dto.TransactionResponse;
import com.dolos.transaction.domain.TransactionEntity;
import com.dolos.transaction.repo.TransactionRepository;
import java.util.UUID;
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

    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID id) {
        return repository
                .findById(id)
                .map(TransactionMapper::toResponse)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }
}
