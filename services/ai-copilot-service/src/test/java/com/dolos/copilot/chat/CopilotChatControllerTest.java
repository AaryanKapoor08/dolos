package com.dolos.copilot.chat;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for the copilot chat endpoint (Phase 4A). The {@link CopilotChatService} (and hence
 * the real Ollama call) is mocked, so this asserts the REST contract — request binding, the reply
 * envelope, and validation — and runs everywhere without a model. The live model round-trip is
 * Docker-verified.
 */
@WebMvcTest(CopilotChatController.class)
// Controller-contract slice: the resource-server chain (Phase 5B) isn't the unit under test, so disable
// the security filters here (401/403 is exercised end-to-end in Docker with real tokens).
@AutoConfigureMockMvc(addFilters = false)
class CopilotChatControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private CopilotChatService chatService;

    @Test
    void returnsModelReply() throws Exception {
        when(chatService.chat("What is structuring?")).thenReturn("Breaking a large sum into smaller deposits.");

        mvc.perform(
                        post("/api/copilot/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"What is structuring?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Breaking a large sum into smaller deposits."));
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mvc.perform(
                        post("/api/copilot/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
