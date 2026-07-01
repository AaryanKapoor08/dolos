package com.dolos.copilot.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dolos.copilot.agent.InvestigateAlertService.InvestigationResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for the investigate-alert endpoint (Phase 4E). {@link InvestigateAlertService} (and so
 * the real model, tools, MinIO and case-service) is mocked, so this asserts the REST contract — request
 * binding, the result envelope (SAR pointer, linked case, step log), and validation — and runs without
 * any infrastructure. The live investigation loop is Docker-verified.
 */
@WebMvcTest(InvestigateController.class)
// Controller-contract slice: disable the Phase 5B resource-server filters (auth is Docker-verified).
@AutoConfigureMockMvc(addFilters = false)
class InvestigateControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private InvestigateAlertService investigateService;

    @Test
    void returnsSarDraftPointerAndLinkedCase() throws Exception {
        UUID alertId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID caseId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(investigateService.investigate(any()))
                .thenReturn(
                        new InvestigationResult(
                                alertId,
                                "ACC-1001",
                                caseId,
                                "s3://sar-drafts/sar/" + alertId + "/1700-abcd1234.md",
                                true,
                                "# Suspicious Activity Report (DRAFT)\n...",
                                List.of("fintrac-structuring-str.txt"),
                                List.of("Resolved alert", "Stored SAR draft")));

        mvc.perform(
                        post("/api/copilot/investigate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"alertId\":\"" + alertId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("ACC-1001"))
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.linkedToCase").value(true))
                .andExpect(jsonPath("$.sarPointer").value(org.hamcrest.Matchers.startsWith("s3://sar-drafts/")))
                .andExpect(jsonPath("$.citations[0]").value("fintrac-structuring-str.txt"))
                .andExpect(jsonPath("$.steps").isArray());
    }

    @Test
    void rejectsMissingAlertId() throws Exception {
        mvc.perform(
                        post("/api/copilot/investigate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
