package com.dolos.reporting.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO client wiring (Phase 6B) — the S3-compatible store the batch writer renders filed reports to.
 * Mirrors the copilot's RAG/SAR-store wiring so the whole platform speaks to one object store.
 */
@Configuration
@EnableConfigurationProperties(ReportingProperties.class)
public class MinioConfig {

    @Bean
    MinioClient minioClient(ReportingProperties props) {
        ReportingProperties.Minio m = props.minio();
        return MinioClient.builder()
                .endpoint(m.endpoint())
                .credentials(m.accessKey(), m.secretKey())
                .build();
    }
}
