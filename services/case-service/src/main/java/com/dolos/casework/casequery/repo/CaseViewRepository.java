package com.dolos.casework.casequery.repo;

import com.dolos.casework.casequery.readmodel.CaseView;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the {@code case_view} read model. Internal to {@code casequery}. */
public interface CaseViewRepository extends JpaRepository<CaseView, UUID> {}
