/**
 * {@code casecmd} — the command side of case-service (Axon, Phase 3B).
 *
 * <p>Holds the event-sourced {@code Case} aggregate, the commands that mutate it
 * ({@code OpenCase}, {@code AssignCase}, {@code AddEvidence}, {@code Escalate}, {@code FileReport},
 * {@code CloseCase}) and the domain events it emits ({@code CaseOpened}, {@code CaseAssigned},
 * {@code EvidenceAdded}, {@code Escalated}, {@code ReportFiled}, {@code CaseClosed}). The events are
 * this module's exposed contract — the query side projects them and the workflow/integration modules
 * react to them.
 *
 * <p>This is the core domain module: it depends on no other application module
 * ({@code allowedDependencies = {}}).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Case Command (Axon aggregate)",
        allowedDependencies = {})
package com.dolos.casework.casecmd;
