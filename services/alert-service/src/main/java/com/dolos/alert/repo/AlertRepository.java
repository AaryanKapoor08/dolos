package com.dolos.alert.repo;

import com.dolos.alert.domain.AlertEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link AlertEntity}. */
public interface AlertRepository extends JpaRepository<AlertEntity, UUID> {

    /** True if an alert already exists for this transaction — the idempotency check. */
    boolean existsByTransactionId(UUID transactionId);
}
