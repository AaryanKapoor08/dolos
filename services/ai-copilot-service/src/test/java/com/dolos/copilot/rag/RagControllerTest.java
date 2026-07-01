package com.dolos.copilot.rag;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dolos.copilot.rag.RegulationIngestionService.IngestSummary;
import com.dolos.copilot.rag.RegulationIngestionService.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for the RAG management/retrieval endpoints (Phase 4B). The ingestion service (and so
 * MinIO, Tika, Ollama and pgvector) is mocked, leaving the REST contract under test; the real
 * ingest + similarity search is Docker-verified.
 */
@WebMvcTest(RagController.class)
// Controller-contract slice: disable the Phase 5B resource-server filters (auth is Docker-verified).
@AutoConfigureMockMvc(addFilters = false)
class RagControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private RegulationIngestionService ingestion;

    @Test
    void ingestReturnsSummary() throws Exception {
        when(ingestion.ingestAll()).thenReturn(new IngestSummary(2, 7, List.of("a.txt", "b.txt")));

        mvc.perform(post("/api/copilot/ingest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(2))
                .andExpect(jsonPath("$.chunks").value(7))
                .andExpect(jsonPath("$.sources[0]").value("a.txt"));
    }

    @Test
    void searchReturnsRankedChunks() throws Exception {
        when(ingestion.search("structuring", 3))
                .thenReturn(List.of(new RetrievedChunk("fintrac.txt", "file an STR ...", 0.80)));

        mvc.perform(get("/api/copilot/search").param("query", "structuring").param("topK", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("fintrac.txt"))
                .andExpect(jsonPath("$[0].score").value(0.80));
    }

    @Test
    void searchRequiresQueryParam() throws Exception {
        mvc.perform(get("/api/copilot/search")).andExpect(status().isBadRequest());
    }
}
