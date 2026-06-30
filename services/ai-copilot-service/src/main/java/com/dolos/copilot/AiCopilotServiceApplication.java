package com.dolos.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for ai-copilot-service (Phase 4) — the AI copilot.
 *
 * <p>A Spring AI application that talks to a local <b>Ollama</b> model ($0 infra). Phase 4 builds it up
 * in steps:
 *
 * <ul>
 *   <li>4A — a {@code ChatClient} over Ollama; {@code POST /api/copilot/chat} round-trips a prompt;
 *   <li>4B/4C — RAG: ingest regulation PDFs (MinIO + Tika) into pgvector, answer grounded with citations;
 *   <li>4D — tool calling into the transaction/alert/case/graph services;
 *   <li>4E — an investigate-alert agent that drafts a SAR to MinIO and links it as case evidence;
 *   <li>4F — an MCP server exposing the platform tools to external MCP clients.
 * </ul>
 *
 * <p>The chat/embedding model and the Ollama endpoint are single Spring properties (application.yml),
 * so swapping the local model for a cloud model later is a configuration change, not a code change.
 *
 * <p>Cross-cutting web infrastructure (the correlation filter, the error envelope handler) lives in
 * this root package, matching the other Dolos services' house style.
 */
@SpringBootApplication
public class AiCopilotServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCopilotServiceApplication.class, args);
    }
}
