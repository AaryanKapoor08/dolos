/**
 * {@code integration} — Kafka in/out for case-service (Phase 3E — placeholder for now).
 *
 * <p>Will consume {@code AlertRaised} and, for HIGH alerts (or an analyst "open case" action), send
 * an {@code OpenCase} command; and publish {@code CaseOpened}/{@code Escalated}/{@code ReportFiled}/
 * {@code CaseClosed} to Kafka using the Spring Modulith event-publication registry as a transactional
 * outbox. Depends on {@code casecmd} (to send commands) and {@code casequery} (to read case state),
 * never the reverse.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Kafka Integration (inbound/outbound)",
        allowedDependencies = {"casecmd", "casequery"})
package com.dolos.casework.integration;
