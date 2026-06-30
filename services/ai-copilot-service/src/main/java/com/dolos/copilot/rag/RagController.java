package com.dolos.copilot.rag;

import com.dolos.copilot.rag.RegulationIngestionService.IngestSummary;
import com.dolos.copilot.rag.RegulationIngestionService.RetrievedChunk;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG management + retrieval API (Phase 4B). {@code POST /api/copilot/ingest} (re)loads the regulation
 * corpus from MinIO into pgvector; {@code GET /api/copilot/search} runs a raw similarity query and
 * returns the top chunks — the 4B Definition of Done. Grounded, cited answers ({@code /ask}) arrive
 * in 4C on top of this retrieval.
 */
@RestController
@RequestMapping("/api/copilot")
public class RagController {

    private final RegulationIngestionService ingestion;

    public RagController(RegulationIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @PostMapping("/ingest")
    public IngestSummary ingest() throws Exception {
        return ingestion.ingestAll();
    }

    @GetMapping("/search")
    public List<RetrievedChunk> search(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "4") int topK) {
        return ingestion.search(query, topK);
    }
}
