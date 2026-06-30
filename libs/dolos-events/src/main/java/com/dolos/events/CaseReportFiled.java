package com.dolos.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A SAR/STR report was filed for an investigation case (Phase 3E), published to
 * {@link Topics#CASES_REPORTED} via the Modulith outbox. Integration contract for the service-internal
 * Axon {@code ReportFiled} domain event.
 *
 * @param caseId           the case id
 * @param reportReference  the filed report's reference
 * @param filedBy          who filed it
 * @param filedAt          when it was filed
 */
public record CaseReportFiled(UUID caseId, String reportReference, String filedBy, Instant filedAt) {

    public CaseReportFiled {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(filedBy, "filedBy must not be null");
        Objects.requireNonNull(filedAt, "filedAt must not be null");
    }
}
