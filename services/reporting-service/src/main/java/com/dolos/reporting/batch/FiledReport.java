package com.dolos.reporting.batch;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A fully-formed, ready-to-file regulatory report — the batch processor's output item, consumed by the
 * composite writer (rendered to MinIO at {@code objectKey}, then upserted into {@code reporting.filed_report}).
 *
 * <p>{@code reportRef} and {@code objectKey} are DETERMINISTIC (derived from type + business date +
 * alert id), which is what makes a re-run idempotent: the same alert always maps to the same object key
 * and the same {@code filed_report} row (upserted on {@code alert_id}).
 */
public record FiledReport(
        String reportRef,
        UUID alertId,
        String accountId,
        String reportType,
        int score,
        LocalDate businessDate,
        String objectKey,
        String objectPointer,
        String narrative) {}
