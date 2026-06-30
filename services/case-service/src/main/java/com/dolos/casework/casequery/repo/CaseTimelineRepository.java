package com.dolos.casework.casequery.repo;

import com.dolos.casework.casequery.readmodel.CaseTimelineEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the {@code case_timeline} read model. Internal to {@code casequery}. */
public interface CaseTimelineRepository extends JpaRepository<CaseTimelineEntry, String> {

    /** The full timeline for a case, in stream order. */
    List<CaseTimelineEntry> findByCaseIdOrderBySequenceAsc(UUID caseId);
}
