package com.dolos.casework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for case-service (Phase 3) — the investigation case management service.
 *
 * <p>It is a <b>Spring Modulith</b> modular monolith. Each direct sub-package of this package is an
 * application module with enforced boundaries:
 *
 * <ul>
 *   <li>{@code casecmd} — the event-sourced {@code Case} aggregate and its commands (Axon command
 *       side, Phase 3B);
 *   <li>{@code casequery} — the {@code CaseView} read model and its projection (Axon query side,
 *       Phase 3C);
 *   <li>{@code workflow} — the BPMN investigation workflow (Flowable, Phase 3D — placeholder);
 *   <li>{@code integration} — Kafka in/out: {@code AlertRaised} → {@code OpenCase} plus the Modulith
 *       event-publication outbox (Phase 3E — placeholder).
 * </ul>
 *
 * <p>Cross-cutting web infrastructure (the correlation filter, the error envelope handler) lives in
 * this root package so it is shared by all modules without becoming a module of its own.
 *
 * <p>Naming note: the package is {@code casework} (and the Postgres schema likewise) rather than
 * {@code case}, which is a reserved word in both Java and SQL.
 */
@SpringBootApplication
public class CaseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CaseServiceApplication.class, args);
    }
}
