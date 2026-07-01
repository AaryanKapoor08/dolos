package com.dolos.copilot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * The copilot's platform tools (Phase 4D): Spring AI {@link Tool}s the local model can invoke to drive
 * Dolos from real data instead of guessing. Each tool is a thin REST call to a downstream service and
 * returns the raw JSON response, which the model reads to compose its answer.
 *
 * <p>Every downstream is secured as of Phase 5B, so each client carries a Keycloak service token from
 * {@link ServiceTokenProvider}: the transaction/alert/graph clients attach it via a request interceptor
 * installed in the constructor, and the case-service calls attach it explicitly. Every tool catches
 * transport/HTTP errors and returns a short human-readable message rather than throwing, so a down
 * dependency degrades the answer instead of failing the whole turn.
 */
@Component
public class PlatformTools {

    private static final Logger log = LoggerFactory.getLogger(PlatformTools.class);

    private final RestClient transactionClient;
    private final RestClient alertClient;
    private final RestClient graphClient;
    private final RestClient caseClient;
    private final ServiceTokenProvider tokens;
    private final ObjectMapper mapper;
    private final int historyLimit;

    public PlatformTools(
            RestClient.Builder builder,
            PlatformProperties props,
            ServiceTokenProvider tokens,
            ObjectMapper mapper) {
        // The transaction/alert/graph services became JWT resource servers in 5B; attach the service
        // token to every call via an interceptor. (case-service calls carry it explicitly, since some
        // are agent-support methods that already set the header.)
        ClientHttpRequestInterceptor bearer =
                (request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokens.accessToken());
                    return execution.execute(request, body);
                };
        this.transactionClient =
                builder.clone().baseUrl(props.transactionBaseUrl()).requestInterceptor(bearer).build();
        this.alertClient =
                builder.clone().baseUrl(props.alertBaseUrl()).requestInterceptor(bearer).build();
        this.graphClient =
                builder.clone().baseUrl(props.graphBaseUrl()).requestInterceptor(bearer).build();
        this.caseClient = builder.clone().baseUrl(props.caseBaseUrl()).build();
        this.tokens = tokens;
        this.mapper = mapper;
        this.historyLimit = props.transactionHistoryLimit();
    }

    @Tool(
            description =
                    "Get the most recent transactions for an account, newest first, as JSON. Use this to"
                        + " inspect an account's recent activity (amounts, counterparties, direction).")
    public String getTransactionHistory(
            @ToolParam(description = "the account id, e.g. ACC-1001") String account) {
        try {
            String body =
                    transactionClient
                            .get()
                            .uri("/api/transactions?accountId={a}&limit={l}", account, historyLimit)
                            .retrieve()
                            .body(String.class);
            return body == null || body.isBlank() ? "[]" : body;
        } catch (Exception e) {
            return error("transaction-service", e);
        }
    }

    @Tool(
            description =
                    "Get the current alert queue (highest risk first) as a JSON array. Each alert has an"
                        + " alertId, alertType (TRANSACTION or RING), severity, score, subject account and"
                        + " reasons.")
    public String getRecentAlerts() {
        try {
            String body = alertClient.get().uri("/api/alerts").retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                return "[]";
            }
            // alert-service returns a paged envelope; hand the model just the content array.
            JsonNode root = mapper.readTree(body);
            JsonNode content = root.has("content") ? root.get("content") : root;
            return mapper.writeValueAsString(content);
        } catch (Exception e) {
            return error("alert-service", e);
        }
    }

    @Tool(
            description =
                    "Get the full details and timeline of an investigation case by its id (a UUID), as"
                        + " JSON: status, assignee, the opening alert/account/score, and every event.")
    public String getCaseDetails(@ToolParam(description = "the case id (UUID)") String caseId) {
        try {
            return caseClient
                    .get()
                    .uri("/api/cases/{id}", caseId)
                    .headers(h -> h.setBearerAuth(tokens.accessToken()))
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound nf) {
            return "No case found with id " + caseId + ".";
        } catch (Exception e) {
            return error("case-service", e);
        }
    }

    @Tool(
            description =
                    "Look up an account's neighborhood in the fraud graph as JSON: whether it is in a"
                        + " detected mule/cash-out ring (inRing + ring ids), its owners, devices, and"
                        + " incoming/outgoing transaction edges. Use this to answer ring-membership"
                        + " questions.")
    public String runGraphQuery(@ToolParam(description = "the account id, e.g. ACC-1001") String account) {
        try {
            return graphClient
                    .get()
                    .uri("/api/graph/account/{id}/neighborhood", account)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            return error("graph-service", e);
        }
    }

    // --- Agent-support operations (Phase 4E) ------------------------------------------------------
    // Typed platform access for the investigate-alert agent. Unlike the @Tool methods above (which the
    // model invokes and which return raw JSON or a friendly error string), these are called directly by
    // the orchestrator and throw on failure so a broken step aborts the investigation loudly.

    /**
     * Look up a single alert by id from the alert queue, returning its JSON node, or {@code null} if no
     * such alert exists. The alert read API only lists (risk-sorted, paged), so this pulls a large page
     * and filters by {@code alertId}.
     */
    public JsonNode findAlert(UUID alertId) {
        try {
            String body =
                    alertClient.get().uri("/api/alerts?size=2000").retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                return null;
            }
            JsonNode root = mapper.readTree(body);
            JsonNode content = root.has("content") ? root.get("content") : root;
            for (JsonNode alert : content) {
                if (alertId.toString().equals(alert.path("alertId").asText())) {
                    return alert;
                }
            }
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("could not read alerts from alert-service: " + e.getMessage(), e);
        }
    }

    /**
     * Find the case opened from {@code alertId} (case-service links each case to its opening alert),
     * returning the case id, or {@code null} if no case has been opened for the alert yet. Carries the
     * service token since case-service is secured (Phase 3F).
     */
    public UUID findCaseIdByAlert(UUID alertId) {
        try {
            String body =
                    caseClient
                            .get()
                            .uri("/api/cases")
                            .headers(h -> h.setBearerAuth(tokens.accessToken()))
                            .retrieve()
                            .body(String.class);
            if (body == null || body.isBlank()) {
                return null;
            }
            for (JsonNode c : mapper.readTree(body)) {
                if (alertId.toString().equals(c.path("alertId").asText())) {
                    return UUID.fromString(c.path("caseId").asText());
                }
            }
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("could not read cases from case-service: " + e.getMessage(), e);
        }
    }

    /** Attach a note (e.g. a pointer to the drafted SAR) to a case as evidence. case-service is secured. */
    public void addCaseEvidence(UUID caseId, String note) {
        caseClient
                .post()
                .uri("/api/cases/{id}/evidence", caseId)
                .headers(h -> h.setBearerAuth(tokens.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("note", note, "addedBy", "copilot"))
                .retrieve()
                .toBodilessEntity();
    }

    private static String error(String service, Exception e) {
        log.warn("Tool call to {} failed: {}", service, e.getMessage());
        return "Could not reach " + service + ": " + e.getMessage();
    }
}
