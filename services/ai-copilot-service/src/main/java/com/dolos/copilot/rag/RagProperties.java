package com.dolos.copilot.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the RAG corpus (Phase 4B): where the regulation documents live (an S3-compatible
 * MinIO bucket) and whether to ingest them on startup. Bound from {@code dolos.rag.*} in application.yml;
 * the container overrides the MinIO endpoint/credentials via {@code DOLOS_RAG_MINIO_*}.
 */
@ConfigurationProperties(prefix = "dolos.rag")
public record RagProperties(Minio minio, boolean ingestOnStartup) {

    public record Minio(String endpoint, String accessKey, String secretKey, String bucket) {}
}
