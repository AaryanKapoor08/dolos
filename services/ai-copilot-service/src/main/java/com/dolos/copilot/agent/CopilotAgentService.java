package com.dolos.copilot.agent;

import com.dolos.copilot.tools.PlatformTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * The tool-calling copilot (Phase 4D). Unlike {@code /chat} (free-form) and {@code /ask} (RAG over the
 * regulation corpus), this client is wired with the {@link PlatformTools}, so the local model can pull
 * live data — transaction history, the alert queue, an account's graph/ring membership, case details —
 * and answer from it. Spring AI runs the tool loop automatically: the model emits tool calls, Spring AI
 * invokes the matching {@code @Tool} methods and feeds the results back until the model produces an answer.
 */
@Service
public class CopilotAgentService {

    private static final String SYSTEM_PROMPT =
            """
            You are Dolos, an AI copilot for fraud and anti-money-laundering (AML) investigators.
            You can call tools to read the live platform: an account's recent transactions, the current
            alert queue, an account's fraud-graph neighborhood (including whether it is in a detected
            ring), and the details of an investigation case.

            Always answer from tool results — never invent account numbers, transactions, alerts, ring
            membership, or case facts. If a tool returns no data or an error, say so plainly rather than
            guessing. Be concise and precise.
            """;

    private final ChatClient chatClient;

    public CopilotAgentService(ChatClient.Builder chatClientBuilder, PlatformTools platformTools) {
        this.chatClient =
                chatClientBuilder.defaultSystem(SYSTEM_PROMPT).defaultTools(platformTools).build();
    }

    /** Answers {@code message}, letting the model invoke platform tools as needed. */
    public String run(String message) {
        return chatClient.prompt().user(message).call().content();
    }
}
