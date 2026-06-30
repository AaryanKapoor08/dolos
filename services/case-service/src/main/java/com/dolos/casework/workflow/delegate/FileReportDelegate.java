package com.dolos.casework.workflow.delegate;

import com.dolos.casework.casecmd.CaseCommandService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Service task on the escalate branch (Phase 3D): dispatches {@code FileReport}, recording the SAR/STR
 * reference before the case is closed.
 */
@Component("fileReportDelegate")
public class FileReportDelegate implements JavaDelegate {

    private final CaseCommandService commands;

    public FileReportDelegate(CaseCommandService commands) {
        this.commands = commands;
    }

    @Override
    public void execute(DelegateExecution execution) {
        commands.fileReport(
                WorkflowVariables.caseId(execution),
                WorkflowVariables.string(execution, "reportReference"),
                WorkflowVariables.actor(execution));
    }
}
