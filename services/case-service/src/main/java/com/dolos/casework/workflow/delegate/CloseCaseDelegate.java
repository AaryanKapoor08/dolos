package com.dolos.casework.workflow.delegate;

import com.dolos.casework.casecmd.CaseCommandService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Terminal service task (Phase 3D): dispatches {@code CloseCase}. Both gateway branches converge here,
 * so every completed process leaves the case in {@code CLOSED} — the workflow's end state matches the
 * aggregate's terminal state.
 */
@Component("closeCaseDelegate")
public class CloseCaseDelegate implements JavaDelegate {

    private final CaseCommandService commands;

    public CloseCaseDelegate(CaseCommandService commands) {
        this.commands = commands;
    }

    @Override
    public void execute(DelegateExecution execution) {
        commands.closeCase(
                WorkflowVariables.caseId(execution),
                WorkflowVariables.string(execution, "resolution"),
                WorkflowVariables.actor(execution));
    }
}
