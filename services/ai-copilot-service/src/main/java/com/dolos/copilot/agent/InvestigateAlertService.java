package com.dolos.copilot.agent;

import com.dolos.copilot.rag.RegulationIngestionService;
import com.dolos.copilot.rag.RegulationIngestionService.RetrievedChunk;
import com.dolos.copilot.tools.PlatformTools;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * The investigate-alert agent (Phase 4E). Given an alert id, it runs a fixed investigation loop —
 * resolve the alert, pull the subject account's transaction history, check the fraud graph for ring
 * membership, retrieve the relevant regulations from the RAG corpus, then ask the model to synthesize a
 * structured <b>SAR (Suspicious Activity Report)</b> draft. The draft is written to MinIO and a pointer
 * to it is attached to the alert's case as evidence (case-service {@code AddEvidence}).
 *
 * <p>The orchestration is deliberately deterministic rather than letting the model drive the tool loop:
 * a fixed pipeline is reliable on the small local model and — per the 4E Definition of Done — makes
 * every step traceable in the logs. The one model call is the final synthesis, grounded only in the
 * evidence gathered by the preceding steps.
 */
@Service
public class InvestigateAlertService {

    private static final Logger log = LoggerFactory.getLogger(InvestigateAlertService.class);

    private static final int REG_TOP_K = 4;

    private static final String SAR_SYSTEM =
            """
            You are Dolos, an AML/fraud investigation copilot drafting a Suspicious Activity Report (SAR)
            for a human analyst to review and file. Write a clear, structured SAR draft in Markdown using
            ONLY the evidence provided (the alert, the account's transactions, the fraud-graph/ring
            findings, and the regulation excerpts). Do not invent transactions, accounts, ring membership
            or facts not present in the evidence.

            Use these sections:
            # Suspicious Activity Report (DRAFT)
            ## 1. Subject
            ## 2. Summary of Suspicious Activity
            ## 3. Transaction Analysis
            ## 4. Network / Ring Analysis
            ## 5. Regulatory Basis
            ## 6. Recommended Action

            In the Regulatory Basis section, cite the supporting regulation source filename(s) in square
            brackets, e.g. [fintrac-structuring-str.txt]. Keep it concise and factual; this is a draft.
            """;

    private final PlatformTools platform;
    private final RegulationIngestionService regulations;
    private final SarDraftStore sarStore;
    private final ChatClient chatClient;

    public InvestigateAlertService(
            PlatformTools platform,
            RegulationIngestionService regulations,
            SarDraftStore sarStore,
            ChatClient.Builder chatClientBuilder) {
        this.platform = platform;
        this.regulations = regulations;
        this.sarStore = sarStore;
        this.chatClient = chatClientBuilder.build();
    }

    /** Run the full investigation for {@code alertId} and return the drafted SAR plus where it landed. */
    public InvestigationResult investigate(UUID alertId) {
        List<String> steps = new ArrayList<>();

        // 1. Resolve the alert.
        JsonNode alert = platform.findAlert(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("no alert found with id " + alertId);
        }
        String account = alert.path("account").path("value").asText();
        if (account.isBlank()) {
            account = alert.path("account").asText();
        }
        step(steps, "Resolved alert " + alertId + " (account " + account + ", type "
                + alert.path("alertType").asText() + ", score " + alert.path("score").asText() + ")");

        // 2. Transaction history for the subject account.
        String history = platform.getTransactionHistory(account);
        step(steps, "Pulled transaction history for " + account);

        // 3. Fraud-graph neighborhood / ring membership.
        String graph = platform.runGraphQuery(account);
        step(steps, "Queried fraud graph for " + account);

        // 4. Retrieve relevant regulations from the RAG corpus.
        String regQuery = buildRegQuery(alert);
        List<RetrievedChunk> chunks = regulations.search(regQuery, REG_TOP_K);
        List<String> citations =
                chunks.stream().map(RetrievedChunk::source).filter(s -> s != null).distinct().toList();
        step(steps, "Retrieved " + chunks.size() + " regulation chunk(s) " + citations);

        // 5. Synthesize the SAR draft from the gathered evidence.
        String evidence = buildEvidence(alertId, account, alert, history, graph, chunks);
        String sar =
                chatClient
                        .prompt()
                        .system(SAR_SYSTEM)
                        .user("Draft a SAR from this evidence:\n\n" + evidence)
                        .call()
                        .content();
        step(steps, "Synthesized SAR draft (" + (sar == null ? 0 : sar.length()) + " chars)");

        // 6. Write the draft to MinIO.
        String pointer = sarStore.store(alertId, sar == null ? "" : sar);
        step(steps, "Stored SAR draft at " + pointer);

        // 7. Link the draft to the alert's case as evidence (best-effort: a case may not exist yet).
        UUID caseId = platform.findCaseIdByAlert(alertId);
        boolean linked = false;
        if (caseId != null) {
            platform.addCaseEvidence(
                    caseId, "SAR draft generated by the AI copilot for alert " + alertId + ": " + pointer);
            linked = true;
            step(steps, "Linked SAR draft to case " + caseId + " as evidence");
        } else {
            step(steps, "No case found for alert " + alertId + "; SAR draft not linked");
        }

        log.info("Investigation of alert {} complete: SAR at {}, case {}", alertId, pointer, caseId);
        return new InvestigationResult(alertId, account, caseId, pointer, linked, sar, citations, steps);
    }

    /** Build the regulation retrieval query from the alert's type and reasons. */
    private static String buildRegQuery(JsonNode alert) {
        String reasons =
                StreamSupport.stream(alert.path("reasons").spliterator(), false)
                        .map(JsonNode::asText)
                        .collect(Collectors.joining(" "));
        return (alert.path("alertType").asText() + " " + alert.path("title").asText() + " " + reasons
                        + " suspicious activity reporting requirements")
                .trim();
    }

    private static String buildEvidence(
            UUID alertId,
            String account,
            JsonNode alert,
            String history,
            String graph,
            List<RetrievedChunk> chunks) {
        String regs =
                chunks.stream()
                        .map(c -> "[" + c.source() + "]\n" + c.text())
                        .collect(Collectors.joining("\n\n"));
        return """
                ALERT (id %s):
                %s

                SUBJECT ACCOUNT: %s

                TRANSACTION HISTORY (JSON):
                %s

                FRAUD GRAPH / RING NEIGHBORHOOD (JSON):
                %s

                RELEVANT REGULATION EXCERPTS:
                %s
                """
                .formatted(alertId, alert.toString(), account, history, graph, regs);
    }

    private static void step(List<String> steps, String message) {
        log.info("[investigate] {}", message);
        steps.add(message);
    }

    /**
     * The outcome of an investigation: the drafted SAR, the {@code s3://} pointer to it in MinIO, the
     * case it was linked to (if any), and the traceable step log.
     */
    public record InvestigationResult(
            UUID alertId,
            String account,
            UUID caseId,
            String sarPointer,
            boolean linkedToCase,
            String sar,
            List<String> citations,
            List<String> steps) {}
}
