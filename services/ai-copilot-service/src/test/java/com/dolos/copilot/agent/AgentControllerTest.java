package com.dolos.copilot.agent;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for the tool-calling copilot endpoint (Phase 4D). The {@link CopilotAgentService} (and
 * hence the real model + tool loop) is mocked, so this asserts the REST contract — request binding, the
 * reply envelope, and validation — and runs everywhere without a model. The live tool-calling round-trip
 * is Docker-verified.
 */
@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private CopilotAgentService agentService;

    @Test
    void returnsToolGroundedReply() throws Exception {
        when(agentService.run("recent transactions for ACC-1001 and is it in a ring?"))
                .thenReturn("ACC-1001 has 3 recent transactions and is a member of ring RING-A-B-C.");

        mvc.perform(
                        post("/api/copilot/agent")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"message\":\"recent transactions for ACC-1001 and is it in a ring?\"}"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.reply")
                                .value(
                                        "ACC-1001 has 3 recent transactions and is a member of ring RING-A-B-C."));
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mvc.perform(
                        post("/api/copilot/agent")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
