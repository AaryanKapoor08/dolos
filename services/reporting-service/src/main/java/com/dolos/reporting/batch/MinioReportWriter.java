package com.dolos.reporting.batch;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Renders each {@link FiledReport} to the MinIO filed-reports bucket (Phase 6B), keyed by the report's
 * deterministic {@code objectKey} so a re-run overwrites the same object rather than duplicating it.
 * Runs FIRST in the composite writer; if a put fails the whole chunk rolls back (nothing is recorded in
 * {@code filed_report}), so a restart re-files cleanly.
 */
@Component
public class MinioReportWriter implements ItemWriter<FiledReport> {

    private static final Logger log = LoggerFactory.getLogger(MinioReportWriter.class);

    private final MinioClient minio;
    private final String bucket;

    public MinioReportWriter(MinioClient minio, com.dolos.reporting.config.ReportingProperties props) {
        this.minio = minio;
        this.bucket = props.minio().bucket();
    }

    @Override
    public void write(Chunk<? extends FiledReport> chunk) throws Exception {
        ensureBucket();
        for (FiledReport report : chunk) {
            byte[] body = report.narrative().getBytes(StandardCharsets.UTF_8);
            try (ByteArrayInputStream in = new ByteArrayInputStream(body)) {
                minio.putObject(
                        PutObjectArgs.builder().bucket(bucket).object(report.objectKey())
                                .stream(in, body.length, -1)
                                .contentType("text/plain")
                                .build());
            }
            log.info("Filed {} to {}", report.reportRef(), report.objectPointer());
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("Created filed-reports bucket '{}'", bucket);
        }
    }
}
