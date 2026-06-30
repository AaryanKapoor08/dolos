package com.dolos.casework.workflow.delegate;

import com.dolos.casework.casecmd.CaseCommandService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Service task on the escalate branch (Phase 3D): dispatches {@code Escalate} for the case. Reached
 * only when the investigate user task completed with {@code escalate == true} (the gateway condition).
 */
@Component("escalateCaseDelegate")
public class EscalateCaseDelegate implements JavaDelegate {

    private final CaseCommandService commands;

    public EscalateCaseDelegate(CaseCommandService commands) {
        this.commands = commands;
    }

    @Override
    public void execute(DelegateExecution execution) {
        commands.escalate(
                WorkflowVariables.caseId(execution),
                WorkflowVariables.string(execution, "reason"),
                WorkflowVariables.actor(execution));
    }
}
