package com.dolos.copilot.rag;

import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the RAG ingestion collaborators (Phase 4B): the MinIO client for the source corpus, the
 * token-based chunk splitter, and a startup runner that performs a one-time ingest when the vector
 * store is empty.
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    @Bean
    MinioClient minioClient(RagProperties props) {
        RagProperties.Minio m = props.minio();
        return MinioClient.builder().endpoint(m.endpoint()).credentials(m.accessKey(), m.secretKey()).build();
    }

    /**
     * Splits each parsed document into embedding-sized chunks. We use ~200-token chunks (vs. the
     * 800-token default) so each regulation's distinct topics — e.g. FINTRAC structuring vs. FinCEN
     * CTR vs. KYC/CDD — get their own embedding and retrieve precisely; a single whole-document vector
     * dilutes the topic signal and mis-ranks closely related documents.
     */
    @Bean
    TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(200, 100, 5, 10000, true);
    }

    /**
     * One-time ingest on startup: only when enabled and the store is empty, so a normal restart does not
     * re-embed the whole corpus. A failure here (MinIO/Ollama not yet reachable on a host run) is logged
     * but never fails boot — the corpus can be (re)loaded later via {@code POST /api/copilot/ingest}.
     */
    @Bean
    ApplicationRunner ragStartupIngest(RegulationIngestionService ingestion, RagProperties props) {
        return args -> {
            if (!props.ingestOnStartup()) {
                return;
            }
            try {
                ingestion.ingestIfEmpty();
            } catch (Exception e) {
                log.warn("Startup RAG ingest skipped: {}", e.getMessage());
            }
        };
    }
}
