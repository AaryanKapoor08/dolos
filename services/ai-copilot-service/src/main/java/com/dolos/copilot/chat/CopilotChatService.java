package com.dolos.copilot.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Thin facade over the Spring AI {@link ChatClient} (Phase 4A). It builds a client from the
 * auto-configured {@link ChatClient.Builder} (wired to Ollama via {@code spring.ai.ollama.*}) with a
 * default system persona, and round-trips a user prompt to the local model.
 *
 * <p>This is the seam the rest of Phase 4 grows from: 4C wires a RAG retrieval advisor here, 4D
 * registers tool callbacks, and 4E layers the investigate-alert agent — all on the same client.
 */
@Service
public class CopilotChatService {

    /** The investigator persona; reused as the system message on every chat. */
    private static final String SYSTEM_PROMPT =
            """
            You are Dolos, an AI copilot for fraud and anti-money-laundering (AML) investigators.
            Be concise, precise and professional. When you are unsure, say so rather than guessing.
            """;

    private final ChatClient chatClient;

    public CopilotChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    }

    /** Sends {@code prompt} to the local model and returns the completion text. */
    public String chat(String prompt) {
        return chatClient.prompt().user(prompt).call().content();
    }
}
