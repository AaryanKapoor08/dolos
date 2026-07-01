package com.dolos.bff.api;

import java.util.List;

/**
 * The BFF's GraphQL object shapes (Phase 5D) as plain Java records — Spring for GraphQL maps each
 * record accessor to the matching schema field (see {@code graphql/schema.graphqls}). These are
 * deliberately flat, string-typed projections of the downstream DTOs: the BFF's job is to reshape and
 * aggregate for one screen, not to re-expose every backend nuance. The composed fields on
 * {@link Alert} ({@code case}, {@code transaction}, {@code accountGraph}) are NOT stored here — they
 * are resolved on demand by {@code @SchemaMapping} methods so a query only pays for what it selects.
 */
public final class GraphTypes {

    private GraphTypes() {}

    /** An alert row, projected from alert-service's {@code AlertResponse}. */
    public record Alert(
            String alertId,
            String alertType,
            String severity,
            String title,
            String transactionId,
            String accountId,
            int score,
            List<String> reasons,
            String detail,
            String raisedAt) {}

    /** A case with its timeline, projected from case-service's {@code CaseDetails}. */
    public record Case(
            String caseId,
            String status,
            String alertId,
            String accountId,
            int score,
            String assignee,
            String openedBy,
            String openedAt,
            String updatedAt,
            String reportReference,
            String resolution,
            List<TimelineItem> timeline) {}

    /** One entry in a case timeline. */
    public record TimelineItem(
            int sequence, String type, String summary, String actor, String occurredAt) {}

    /** A transaction, projected from transaction-service's {@code TransactionResponse}. */
    public record Transaction(
            String id,
            String account,
            String counterparty,
            String amount,
            String currency,
            String direction,
            String description,
            String occurredAt) {}

    /** An account's one-hop fraud-graph neighbourhood, projected from graph-service. */
    public record AccountGraph(
            String accountId,
            boolean inRing,
            List<String> rings,
            List<String> owners,
            List<String> devices,
            List<GraphEdge> outgoing,
            List<GraphEdge> incoming) {}

    /** One directed money-flow edge in an account's neighbourhood. */
    public record GraphEdge(String counterparty, String amount, String occurredAt) {}

    /** The copilot's answer to a natural-language question (the agent's reply). */
    public record CopilotAnswer(String reply) {}

    /** Input for the {@code copilot} mutation. */
    public record CopilotInput(String question) {}
}
