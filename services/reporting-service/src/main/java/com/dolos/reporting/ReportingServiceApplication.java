package com.dolos.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * reporting-service (Phase 6B): the FORMAL regulatory-filing service. A Spring Batch application
 * whose nightly {@code sarFilingJob} reads HIGH-severity alerts from the alert read model, renders a
 * SAR/STR for each, and files it to MinIO + the {@code reporting} schema.
 *
 * <p>{@link EnableScheduling} drives the nightly run; the job is also launchable on demand via the REST
 * trigger. Boot auto-configures the JDBC-backed Spring Batch {@code JobRepository} (no
 * {@code @EnableBatchProcessing} — that would switch off the auto-configuration).
 */
@SpringBootApplication
@EnableScheduling
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}
