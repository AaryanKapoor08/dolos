package com.dolos.casework.workflow.delegate;

import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;

/**
 * Shared accessors for the process variables the investigation BPMN carries (Phase 3D). The case id
 * is stored as a {@code String} (Flowable variables are JSON-friendly scalars) and parsed back to a
 * {@link UUID}; the {@code actor} names whoever is driving the workflow and defaults to
 * {@code "workflow"} so the audit trail is never blank.
 */
final class WorkflowVariables {

    private WorkflowVariables() {}

    static UUID caseId(DelegateExecution execution) {
        return UUID.fromString((String) execution.getVariable("caseId"));
    }

    static String actor(DelegateExecution execution) {
        Object actor = execution.getVariable("actor");
        return actor == null ? "workflow" : actor.toString();
    }

    static String string(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        return value == null ? null : value.toString();
    }
}
