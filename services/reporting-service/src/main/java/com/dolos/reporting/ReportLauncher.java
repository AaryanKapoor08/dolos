package com.dolos.reporting;

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@code sarFilingJob} (Phase 6B): nightly on a cron, and on demand from the REST trigger.
 *
 * <p>{@code businessDate} is the sole identifying job parameter, which gives the two guarantees:
 * <ul>
 *   <li><b>Idempotent</b> — relaunching a business date that already COMPLETED throws
 *       {@link JobInstanceAlreadyCompleteException}, which we swallow and report as "already filed";
 *       nothing is re-filed. (The writers are independently idempotent too — deterministic MinIO keys +
 *       an {@code ON CONFLICT} upsert.)</li>
 *   <li><b>Restartable</b> — relaunching a date whose run FAILED resumes that same JobInstance.</li>
 * </ul>
 */
@Component
public class ReportLauncher {

    private static final Logger log = LoggerFactory.getLogger(ReportLauncher.class);

    private final JobLauncher jobLauncher;
    private final Job sarFilingJob;

    public ReportLauncher(JobLauncher jobLauncher, Job sarFilingJob) {
        this.jobLauncher = jobLauncher;
        this.sarFilingJob = sarFilingJob;
    }

    /** Nightly run (default 02:00) — files the day that just ended. */
    @Scheduled(cron = "${dolos.reporting.cron}")
    public void nightly() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        log.info("Nightly SAR/STR filing run for business date {}", yesterday);
        fileFor(yesterday);
    }

    /** Launch (or restart) the filing job for a business date, summarizing the outcome. */
    public LaunchResult fileFor(LocalDate businessDate) {
        JobParameters params =
                new JobParametersBuilder().addString("businessDate", businessDate.toString())
                        .toJobParameters();
        try {
            JobExecution exec = jobLauncher.run(sarFilingJob, params);
            long read = 0;
            long written = 0;
            for (StepExecution step : exec.getStepExecutions()) {
                read += step.getReadCount();
                written += step.getWriteCount();
            }
            return new LaunchResult(
                    businessDate.toString(), exec.getStatus().toString(), read, written, null);
        } catch (JobInstanceAlreadyCompleteException e) {
            // Idempotent no-op: this date was already filed.
            return new LaunchResult(
                    businessDate.toString(), "ALREADY_COMPLETED", 0, 0, "already filed for this date");
        } catch (Exception e) {
            log.error("SAR/STR filing failed for {}: {}", businessDate, e.getMessage(), e);
            return new LaunchResult(businessDate.toString(), "FAILED", 0, 0, e.getMessage());
        }
    }

    /** Summary of one launch, returned by the REST trigger. */
    public record LaunchResult(
            String businessDate, String status, long alertsRead, long reportsFiled, String message) {}
}
