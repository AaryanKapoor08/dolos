package com.dolos.transaction.repo;

import com.dolos.transaction.domain.TransactionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link TransactionEntity}. */
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    /**
     * The most recent transactions for an account, newest first. {@link Pageable} caps the result
     * size — the copilot's {@code getTransactionHistory} tool (Phase 4D) asks for a bounded window
     * rather than a whole account history.
     */
    List<TransactionEntity> findByAccountIdOrderByOccurredAtDesc(String accountId, Pageable pageable);
}
