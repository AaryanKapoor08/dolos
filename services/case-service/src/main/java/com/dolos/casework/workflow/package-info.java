/**
 * {@code workflow} — the BPMN investigation workflow (Flowable, Phase 3D — placeholder for now).
 *
 * <p>Will embed a Flowable process ({@code Assign → Investigate → Escalate? → File Report → Close})
 * whose service tasks dispatch {@code casecmd} commands and whose user tasks gate analyst actions.
 * Depends on {@code casecmd} (it drives the aggregate through commands), never the reverse.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Investigation Workflow (BPMN)",
        allowedDependencies = {"casecmd"})
package com.dolos.casework.workflow;
