package com.dolos.copilot.qa;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dolos.copilot.qa.CopilotAskService.GroundedAnswer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for the grounded-answer endpoint (Phase 4C). The QA service (and so retrieval + the
 * model) is mocked; this asserts the {@code /ask} contract — the answer-with-citations envelope and
 * validation. The real grounded RAG round-trip (cited answer + refusing an unrelated question) is
 * Docker-verified.
 */
@WebMvcTest(AskController.class)
// Controller-contract slice: disable the Phase 5B resource-server filters (auth is Docker-verified).
@AutoConfigureMockMvc(addFilters = false)
class AskControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private CopilotAskService askService;

    @Test
    void returnsGroundedAnswerWithCitations() throws Exception {
        when(askService.ask("What must be reported for structuring under FINTRAC?"))
                .thenReturn(new GroundedAnswer("File an STR. [fintrac-structuring-str.txt]",
                        List.of("fintrac-structuring-str.txt")));

        mvc.perform(
                        post("/api/copilot/ask")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"What must be reported for structuring under FINTRAC?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("File an STR. [fintrac-structuring-str.txt]"))
                .andExpect(jsonPath("$.citations[0]").value("fintrac-structuring-str.txt"));
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        mvc.perform(
                        post("/api/copilot/ask")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
