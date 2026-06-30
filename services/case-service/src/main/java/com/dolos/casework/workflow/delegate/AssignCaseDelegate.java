package com.dolos.casework.workflow.delegate;

import com.dolos.casework.casecmd.CaseCommandService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Service task: assigns the case to an analyst (Phase 3D). Dispatches {@code AssignCase} through the
 * {@link CaseCommandService} so the workflow drives the same event-sourced aggregate as the REST API.
 *
 * <p>Referenced by bean name from the BPMN ({@code flowable:delegateExpression="${assignCaseDelegate}"}).
 * Read-only of process variables (no field injection), so this shared singleton is thread-safe.
 */
@Component("assignCaseDelegate")
public class AssignCaseDelegate implements JavaDelegate {

    private final CaseCommandService commands;

    public AssignCaseDelegate(CaseCommandService commands) {
        this.commands = commands;
    }

    @Override
    public void execute(DelegateExecution execution) {
        commands.assign(
                WorkflowVariables.caseId(execution),
                WorkflowVariables.string(execution, "assignee"),
                WorkflowVariables.actor(execution));
    }
}
