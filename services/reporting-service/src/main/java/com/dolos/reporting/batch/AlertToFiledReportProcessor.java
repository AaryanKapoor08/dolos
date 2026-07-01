package com.dolos.reporting.batch;

import java.time.LocalDate;
import org.springframework.batch.item.ItemProcessor;

/**
 * Turns an alert (read model row) into a filed SAR/STR (Phase 6B). A transaction-scoped alert files a
 * <b>STR</b> (Suspicious Transaction Report — one transaction); an account/ring alert files a <b>SAR</b>
 * (Suspicious Activity Report). The report reference and MinIO object key are derived deterministically
 * so a re-run of the same business date overwrites, never duplicates.
 *
 * <p>Step-scoped: the {@code businessDate} job parameter and the MinIO bucket are bound per run.
 */
public class AlertToFiledReportProcessor implements ItemProcessor<AlertRow, FiledReport> {

    private final LocalDate businessDate;
    private final String bucket;

    public AlertToFiledReportProcessor(LocalDate businessDate, String bucket) {
        this.businessDate = businessDate;
        this.bucket = bucket;
    }

    @Override
    public FiledReport process(AlertRow alert) {
        String type = alert.transactionId() != null ? "STR" : "SAR";
        String shortId = alert.alertId().toString().substring(0, 8);
        String reportRef = "%s-%s-%s".formatted(type, businessDate, shortId);
        String objectKey = "%s/%s/%s.txt".formatted(type.toLowerCase(), businessDate, reportRef);
        String objectPointer = "s3://" + bucket + "/" + objectKey;
        String narrative = buildNarrative(alert, type, reportRef);
        return new FiledReport(
                reportRef,
                alert.alertId(),
                alert.accountId(),
                type,
                alert.score(),
                businessDate,
                objectKey,
                objectPointer,
                narrative);
    }

    private String buildNarrative(AlertRow alert, String type, String reportRef) {
        String longName =
                type.equals("STR")
                        ? "SUSPICIOUS TRANSACTION REPORT (STR)"
                        : "SUSPICIOUS ACTIVITY REPORT (SAR)";
        StringBuilder sb = new StringBuilder();
        sb.append("FILED ").append(longName).append('\n');
        sb.append("Report reference : ").append(reportRef).append('\n');
        sb.append("Business date    : ").append(businessDate).append('\n');
        sb.append("Subject account  : ").append(alert.accountId()).append('\n');
        sb.append("Source alert     : ").append(alert.alertId());
        if (alert.transactionId() != null) {
            sb.append(" (transaction ").append(alert.transactionId()).append(')');
        }
        sb.append('\n');
        sb.append("Risk score       : ")
                .append(alert.score())
                .append(" (severity ")
                .append(alert.severity())
                .append(")\n\n");
        sb.append("Headline: ").append(alert.title()).append("\n\n");
        sb.append("Triggering typologies:\n");
        for (String reason : alert.reasons().split("\\R")) {
            if (!reason.isBlank()) {
                sb.append("  - ").append(reason.trim()).append('\n');
            }
        }
        if (alert.detail() != null && !alert.detail().isBlank()) {
            sb.append("\nAdditional detail:\n").append(alert.detail().trim()).append('\n');
        }
        sb.append(
                "\nThis report was filed by the automated Dolos reporting pipeline pursuant to the"
                        + " institution's AML program. It supersedes any earlier ai-copilot DRAFT for the"
                        + " same alert.\n");
        return sb.toString();
    }
}
