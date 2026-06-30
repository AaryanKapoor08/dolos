package com.dolos.copilot.rag;

import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The RAG ingestion job (Phase 4B): reads regulation documents from the MinIO bucket, parses each with
 * Apache Tika, splits it into chunks, embeds the chunks via Ollama, and stores the vectors in pgvector.
 *
 * <p>Ingestion is idempotent per source: a re-ingest deletes the existing chunks for a document (matched
 * on the {@code source} metadata) before re-adding, so {@code POST /api/copilot/ingest} can be run
 * repeatedly without duplicating vectors.
 */
@Service
public class RegulationIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RegulationIngestionService.class);

    /** Tika can parse far more, but the corpus is limited to these to skip markers like .gitkeep. */
    private static final List<String> SUPPORTED = List.of(".pdf", ".txt", ".md", ".html", ".htm", ".docx");

    private static final String SOURCE_KEY = "source";

    private final MinioClient minio;
    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;
    private final JdbcTemplate jdbc;
    private final RagProperties props;

    public RegulationIngestionService(
            MinioClient minio,
            VectorStore vectorStore,
            TokenTextSplitter splitter,
            JdbcTemplate jdbc,
            RagProperties props) {
        this.minio = minio;
        this.vectorStore = vectorStore;
        this.splitter = splitter;
        this.jdbc = jdbc;
        this.props = props;
    }

    /** Number of chunks currently stored. */
    public long count() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM copilot.vector_store", Long.class);
        return n == null ? 0 : n;
    }

    /** Ingest the corpus only if the store is empty (the startup path). */
    public IngestSummary ingestIfEmpty() throws Exception {
        if (count() > 0) {
            log.info("RAG store already populated ({} chunks); skipping startup ingest", count());
            return new IngestSummary(0, 0, List.of());
        }
        return ingestAll();
    }

    /** Read every supported object in the bucket and (re)ingest it. Idempotent per source. */
    public IngestSummary ingestAll() throws Exception {
        String bucket = props.minio().bucket();
        int documents = 0;
        int chunks = 0;
        List<String> sources = new ArrayList<>();

        for (Result<Item> result : minio.listObjects(
                ListObjectsArgs.builder().bucket(bucket).recursive(true).build())) {
            Item item = result.get();
            String name = item.objectName();
            if (item.isDir() || !isSupported(name)) {
                continue;
            }

            byte[] bytes = download(bucket, name);
            Resource resource = new NamedByteArrayResource(bytes, name);
            List<Document> parsed = new TikaDocumentReader(resource).get();
            parsed.forEach(d -> d.getMetadata().put(SOURCE_KEY, name));

            List<Document> split = splitter.apply(parsed);
            // Replace any prior chunks for this source so a re-ingest can't duplicate vectors.
            vectorStore.delete(new FilterExpressionBuilder().eq(SOURCE_KEY, name).build());
            vectorStore.add(split);

            documents++;
            chunks += split.size();
            sources.add(name);
            log.info("Ingested '{}' from bucket '{}' -> {} chunks", name, bucket, split.size());
        }

        log.info("RAG ingest complete: {} documents, {} chunks", documents, chunks);
        return new IngestSummary(documents, chunks, sources);
    }

    /** Similarity search over the corpus — the 4B Definition of Done. */
    public List<RetrievedChunk> search(String query, int topK) {
        List<Document> hits =
                vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK).build());
        List<RetrievedChunk> out = new ArrayList<>(hits.size());
        for (Document d : hits) {
            Object source = d.getMetadata().get(SOURCE_KEY);
            out.add(new RetrievedChunk(source == null ? null : source.toString(), d.getText(), d.getScore()));
        }
        return out;
    }

    private byte[] download(String bucket, String name) throws Exception {
        try (InputStream in =
                minio.getObject(GetObjectArgs.builder().bucket(bucket).object(name).build())) {
            return in.readAllBytes();
        }
    }

    private static boolean isSupported(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return SUPPORTED.stream().anyMatch(lower::endsWith);
    }

    /** A {@link ByteArrayResource} that reports a filename so Tika can detect the content type. */
    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    /** Summary of an ingest run. */
    public record IngestSummary(int documents, int chunks, List<String> sources) {}

    /** A chunk returned by a similarity query, with its source document and similarity score. */
    public record RetrievedChunk(String source, String text, Double score) {}
}
