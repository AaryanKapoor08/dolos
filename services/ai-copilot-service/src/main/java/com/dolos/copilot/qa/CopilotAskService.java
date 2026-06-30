package com.dolos.copilot.qa;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Grounded question answering over the regulation corpus (Phase 4C) — Retrieval-Augmented Generation.
 *
 * <p>For each question it retrieves the most similar chunks from pgvector, then asks the model to answer
 * <b>using only that context</b> and to cite the source documents. Two guards keep it from hallucinating
 * regulation content for an unrelated question: a similarity threshold (nothing relevant retrieved → a
 * fixed "not covered" reply, no model call) and a grounding system prompt that forbids outside knowledge.
 */
@Service
public class CopilotAskService {

    private static final int TOP_K = 4;

    /**
     * Minimum cosine similarity (Spring AI normalises to [0,1], higher = closer) for a chunk to count as
     * relevant. AML-related chunks score ~0.55–0.80 against an on-topic question; an off-topic question
     * falls below this, so the corpus is treated as not covering it.
     */
    private static final double SIMILARITY_THRESHOLD = 0.45;

    private static final String GROUNDING_SYSTEM =
            """
            You are Dolos, an AML/fraud investigation copilot. Answer the user's question USING ONLY the
            regulation excerpts provided in the context. If the context does not contain the answer, say
            that the loaded regulations do not cover it — do NOT use outside knowledge and do NOT guess.
            Be concise. Cite the supporting source filename(s) in square brackets, e.g. [fintrac-structuring-str.txt].
            """;

    private static final String NOT_COVERED =
            "The loaded regulations do not cover that. Try an AML/KYC question, "
                    + "or ingest more documents into the corpus.";

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public CopilotAskService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public GroundedAnswer ask(String question) {
        List<Document> docs =
                vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(question)
                                .topK(TOP_K)
                                .similarityThreshold(SIMILARITY_THRESHOLD)
                                .build());

        if (docs == null || docs.isEmpty()) {
            return new GroundedAnswer(NOT_COVERED, List.of());
        }

        String context =
                docs.stream()
                        .map(d -> "[" + source(d) + "]\n" + d.getText())
                        .collect(Collectors.joining("\n\n"));

        String answer =
                chatClient
                        .prompt()
                        .system(GROUNDING_SYSTEM)
                        .user("Question: " + question + "\n\nContext (regulation excerpts):\n" + context)
                        .call()
                        .content();

        List<String> citations = docs.stream().map(this::source).distinct().toList();
        return new GroundedAnswer(answer, citations);
    }

    private String source(Document d) {
        Object s = d.getMetadata().get("source");
        return s == null ? "unknown" : s.toString();
    }

    /** A grounded answer plus the source documents it was grounded in. */
    public record GroundedAnswer(String answer, List<String> citations) {}
}
