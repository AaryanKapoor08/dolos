package com.dolos.bff.service;

import com.dolos.bff.api.GraphTypes.AccountGraph;
import com.dolos.bff.api.GraphTypes.Alert;
import com.dolos.bff.api.GraphTypes.Case;
import com.dolos.bff.api.GraphTypes.CopilotAnswer;
import com.dolos.bff.api.GraphTypes.GraphEdge;
import com.dolos.bff.api.GraphTypes.TimelineItem;
import com.dolos.bff.api.GraphTypes.Transaction;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Calls the business services and projects their JSON into the BFF's flat GraphQL {@code GraphTypes}
 * (Phase 5D). Everything is reactive ({@code Mono}) and every downstream call goes through the
 * load-balanced, token-relaying {@code dolosWebClient}. A {@code 404} downstream is mapped to an empty
 * {@code Mono} so a missing case/transaction resolves to GraphQL {@code null} rather than an error — a
 * BFF should degrade a field, not fail the whole query.
 *
 * <p>Note: alert-service exposes only a paged queue (no GET-by-id), so {@link #alertById(String)} pages
 * the queue and filters — fine at demo scale; a dedicated endpoint would replace it under load.
 */
@Service
public class BackendClient {

    private final WebClient web;

    public BackendClient(WebClient dolosWebClient) {
        this.web = dolosWebClient;
    }

    // --- Queue + single lookups ------------------------------------------------------------------

    /** The risk-sorted alert queue (alert-service returns a Spring {@code PagedModel}). */
    public Mono<List<Alert>> alertQueue(int size) {
        return web.get()
                .uri("lb://alert-service/api/alerts?size={size}", size)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(
                        body -> {
                            List<Alert> alerts = new ArrayList<>();
                            for (JsonNode n : body.path("content")) {
                                alerts.add(toAlert(n));
                            }
                            return alerts;
                        })
                .onErrorResume(BackendClient::empty);
    }

    /** A single alert by id — paged from the queue and filtered (see class note). */
    public Mono<Alert> alertById(String alertId) {
        return alertQueue(200)
                .flatMap(
                        alerts ->
                                alerts.stream()
                                        .filter(a -> alertId.equals(a.alertId()))
                                        .findFirst()
                                        .map(Mono::just)
                                        .orElseGet(Mono::empty));
    }

    /** A case by id (case-service CQRS read model). */
    public Mono<Case> caseById(String caseId) {
        return web.get()
                .uri("lb://case-service/api/cases/{id}", caseId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::toCase)
                .onErrorResume(BackendClient::empty);
    }

    /** The case opened from a given alert, if any (case-service has no by-alert endpoint — filter the list). */
    public Mono<Case> caseByAlertId(String alertId) {
        return web.get()
                .uri("lb://case-service/api/cases")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(
                        body -> {
                            for (JsonNode n : body) {
                                if (alertId.equals(text(n, "alertId"))) {
                                    return Mono.just(toCase(n));
                                }
                            }
                            return Mono.<Case>empty();
                        })
                .onErrorResume(BackendClient::empty);
    }

    /** A transaction by id (transaction-service). */
    public Mono<Transaction> transactionById(String id) {
        return web.get()
                .uri("lb://transaction-service/api/transactions/{id}", id)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::toTransaction)
                .onErrorResume(BackendClient::empty);
    }

    /** An account's one-hop fraud-graph neighbourhood (graph-service). */
    public Mono<AccountGraph> accountGraph(String accountId) {
        return web.get()
                .uri("lb://graph-service/api/graph/account/{id}/neighborhood", accountId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::toAccountGraph)
                .onErrorResume(BackendClient::empty);
    }

    /** Ask the AI copilot agent a natural-language question (ai-copilot-service). */
    public Mono<CopilotAnswer> copilot(String question) {
        return web.post()
                .uri("lb://ai-copilot-service/api/copilot/agent")
                .bodyValue(Map.of("message", question))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(n -> new CopilotAnswer(text(n, "reply")))
                .onErrorResume(
                        WebClientResponseException.class,
                        e -> Mono.just(new CopilotAnswer("The copilot is unavailable right now.")));
    }

    // --- Projections -----------------------------------------------------------------------------

    private Alert toAlert(JsonNode n) {
        return new Alert(
                text(n, "alertId"),
                text(n, "alertType"),
                text(n, "severity"),
                text(n, "title"),
                text(n, "transactionId"),
                // AlertResponse.account is the AccountId value object: {"value":"acc-1"}.
                textAt(n, "account", "value"),
                intAt(n, "score"),
                strings(n, "reasons"),
                text(n, "detail"),
                text(n, "raisedAt"));
    }

    private Case toCase(JsonNode n) {
        List<TimelineItem> timeline = new ArrayList<>();
        for (JsonNode t : n.path("timeline")) {
            timeline.add(
                    new TimelineItem(
                            (int) t.path("sequence").asLong(),
                            text(t, "type"),
                            text(t, "summary"),
                            text(t, "actor"),
                            text(t, "occurredAt")));
        }
        return new Case(
                text(n, "caseId"),
                text(n, "status"),
                text(n, "alertId"),
                text(n, "accountId"),
                intAt(n, "score"),
                text(n, "assignee"),
                text(n, "openedBy"),
                text(n, "openedAt"),
                text(n, "updatedAt"),
                text(n, "reportReference"),
                text(n, "resolution"),
                timeline);
    }

    private Transaction toTransaction(JsonNode n) {
        return new Transaction(
                text(n, "id"),
                textAt(n, "account", "value"),
                textAt(n, "counterparty", "value"),
                // TransactionResponse.amount is the Money value object: {"amount":..,"currency":"USD"}.
                textAt(n, "amount", "amount"),
                textAt(n, "amount", "currency"),
                text(n, "direction"),
                text(n, "description"),
                text(n, "occurredAt"));
    }

    private AccountGraph toAccountGraph(JsonNode n) {
        return new AccountGraph(
                text(n, "accountId"),
                n.path("inRing").asBoolean(false),
                strings(n, "rings"),
                strings(n, "owners"),
                strings(n, "devices"),
                edges(n.path("outgoing")),
                edges(n.path("incoming")));
    }

    private List<GraphEdge> edges(JsonNode arr) {
        List<GraphEdge> edges = new ArrayList<>();
        for (JsonNode e : arr) {
            edges.add(
                    new GraphEdge(
                            text(e, "counterparty"), text(e, "amount"), text(e, "occurredAt")));
        }
        return edges;
    }

    // --- JsonNode helpers ------------------------------------------------------------------------

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String textAt(JsonNode n, String field, String nested) {
        JsonNode v = n.path(field).get(nested);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static int intAt(JsonNode n, String field) {
        return n.path(field).asInt(0);
    }

    private static List<String> strings(JsonNode n, String field) {
        List<String> out = new ArrayList<>();
        for (JsonNode v : n.path(field)) {
            out.add(v.asText());
        }
        return out;
    }

    /**
     * A downstream error (typically a {@code 404} for a missing case/transaction/account) becomes an
     * empty result, so the GraphQL field resolves to {@code null} rather than failing the whole query —
     * a BFF degrades a field, it doesn't fail the screen.
     */
    private static <T> Mono<T> empty(Throwable error) {
        return Mono.empty();
    }
}
