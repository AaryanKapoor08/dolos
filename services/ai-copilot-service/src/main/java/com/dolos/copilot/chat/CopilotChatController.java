package com.dolos.copilot.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The copilot's chat REST API (Phase 4A). {@code POST /api/copilot/chat} round-trips a free-text
 * prompt through the local Ollama model and returns the completion. Grounded RAG answers
 * ({@code /api/copilot/ask}) and the investigate-alert agent arrive in later Phase 4 steps.
 *
 * <p>The copilot is internal at this phase (no auth); it goes behind the gateway in Phase 5.
 */
@RestController
@RequestMapping("/api/copilot")
public class CopilotChatController {

    private final CopilotChatService chatService;

    public CopilotChatController(CopilotChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(chatService.chat(request.message()));
    }

    /** A single free-text turn from the caller. */
    public record ChatRequest(@NotBlank String message) {}

    /** The model's completion. */
    public record ChatResponse(String reply) {}
}
