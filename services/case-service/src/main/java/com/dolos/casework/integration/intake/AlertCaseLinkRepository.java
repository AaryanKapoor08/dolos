package com.dolos.casework.integration.intake;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the {@link AlertCaseLink} alert→case idempotency records (Phase 3E). */
public interface AlertCaseLinkRepository extends JpaRepository<AlertCaseLink, UUID> {}
