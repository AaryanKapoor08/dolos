/**
 * {@code casequery} — the query side of case-service (Axon CQRS projection, Phase 3C).
 *
 * <p>Projects the {@code casecmd} domain events into a denormalized {@code CaseView} read model
 * (status, assignee, timeline, linked alert/transactions) and serves queries over it, including Axon
 * subscription queries for live updates. Depends on {@code casecmd} only — to consume its events.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Case Query (CQRS read model)",
        allowedDependencies = {"casecmd"})
package com.dolos.casework.casequery;
