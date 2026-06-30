package com.dolos.copilot.agent;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Persists drafted SAR (Suspicious Activity Report) documents to MinIO (Phase 4E). Reuses the
 * {@link MinioClient} that already serves the RAG corpus, but writes to a dedicated SAR bucket so the
 * generated drafts never mix with the read-only regulation source. The bucket is created on startup if
 * absent (idempotent), mirroring how {@code minio-init} provisions the regulations bucket.
 *
 * <p>A stored draft is addressed by an {@code s3://<bucket>/<key>} pointer; that pointer is what the
 * agent attaches to the case as evidence (rather than the full document body).
 */
@Component
public class SarDraftStore {

    private static final Logger log = LoggerFactory.getLogger(SarDraftStore.class);

    private final MinioClient minio;
    private final String bucket;

    public SarDraftStore(MinioClient minio, InvestigationProperties props) {
        this.minio = minio;
        this.bucket = props.sarBucket();
    }

    /** Create the SAR bucket if it doesn't exist yet, so the first investigation can write to it. */
    @PostConstruct
    void ensureBucket() {
        try {
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created SAR bucket '{}'", bucket);
            }
        } catch (Exception e) {
            // Non-fatal on boot (MinIO may not be reachable yet on a host run); the write path retries.
            log.warn("Could not ensure SAR bucket '{}' on startup: {}", bucket, e.getMessage());
        }
    }

    /**
     * Write a SAR draft for {@code alertId} and return an {@code s3://}-style pointer to it. The object
     * key namespaces by alert and carries a timestamp + short id so repeated investigations of the same
     * alert don't overwrite each other.
     */
    public String store(UUID alertId, String markdown) {
        String key =
                "sar/%s/%s-%s.md"
                        .formatted(
                                alertId,
                                Instant.now().toEpochMilli(),
                                UUID.randomUUID().toString().substring(0, 8));
        byte[] body = markdown.getBytes(StandardCharsets.UTF_8);
        try {
            minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        } catch (Exception ignored) {
            // Bucket already exists (the common case) — proceed to the put.
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(body)) {
            minio.putObject(
                    PutObjectArgs.builder().bucket(bucket).object(key).stream(in, body.length, -1)
                            .contentType("text/markdown")
                            .build());
        } catch (Exception e) {
            throw new IllegalStateException("could not write SAR draft to MinIO: " + e.getMessage(), e);
        }
        String pointer = "s3://" + bucket + "/" + key;
        log.info("Stored SAR draft for alert {} at {}", alertId, pointer);
        return pointer;
    }
}
