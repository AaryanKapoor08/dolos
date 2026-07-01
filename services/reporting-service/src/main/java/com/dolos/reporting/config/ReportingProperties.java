package com.dolos.reporting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound {@code dolos.reporting.*} configuration (Phase 6B): the nightly cron and the MinIO coordinates
 * for the filed-report object store.
 */
@ConfigurationProperties(prefix = "dolos.reporting")
public record ReportingProperties(String cron, Minio minio) {

    public record Minio(String endpoint, String accessKey, String secretKey, String bucket) {}
}
