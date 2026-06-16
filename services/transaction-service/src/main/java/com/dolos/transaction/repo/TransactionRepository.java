package com.dolos.transaction.repo;

import com.dolos.transaction.domain.TransactionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link TransactionEntity}. */
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {}
