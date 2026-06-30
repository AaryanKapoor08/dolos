package com.dolos.casework.workflow;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

/**
 * The {@code workflow} module's facade over the Flowable engine (Phase 3D). It starts the
 * {@code case-investigation} BPMN process for an already-opened case and completes its analyst-gated
 * user task, letting the embedded engine drive the rest of the lifecycle through the service-task
 * delegates (which dispatch Axon commands). The process is correlated to the case by
 * {@code businessKey == caseId}, so the workflow and the event-sourced aggregate stay in lock-step.
 *
 * <p>This is the module's exposed API; the Flowable {@code RuntimeService}/{@code TaskService} stay an
 * internal detail.
 */
@Service
public class CaseWorkflowService {

    /** Must match the {@code id} of the process in {@code resources/processes/case-investigation.bpmn20.xml}. */
    public static final String PROCESS_KEY = "case-investigation";

    /** Must match the investigate user task's {@code id} in the BPMN. */
    public static final String INVESTIGATE_TASK = "investigateTask";

    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public CaseWorkflowService(RuntimeService runtimeService, TaskService taskService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    /**
     * Starts an investigation for {@code caseId}: the engine runs the assign service task and then
     * waits at the investigate user task.
     *
     * @return the Flowable process-instance id
     */
    public String startInvestigation(UUID caseId, String assignee, String actor) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("caseId", caseId.toString());
        variables.put("assignee", assignee);
        variables.put("actor", actor);
        ProcessInstance instance =
                runtimeService.startProcessInstanceByKey(PROCESS_KEY, caseId.toString(), variables);
        return instance.getId();
    }

    /**
     * Completes the analyst's investigate task, routing the case through the escalate-vs-close gateway.
     * When {@code escalate} is true the engine runs escalate → file-report → close; otherwise it closes
     * directly.
     */
    public void completeInvestigation(
            UUID caseId,
            boolean escalate,
            String reason,
            String reportReference,
            String resolution,
            String actor) {
        Task task = activeInvestigateTask(caseId);
        if (task == null) {
            throw new IllegalStateException("no active investigation task for case " + caseId);
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("escalate", escalate);
        variables.put("reason", reason);
        variables.put("reportReference", reportReference);
        variables.put("resolution", resolution);
        variables.put("actor", actor);
        taskService.complete(task.getId(), variables);
    }

    /** The id of the user task the process is currently waiting on, if any (for status display). */
    public Optional<String> activeTask(UUID caseId) {
        Task task =
                taskService
                        .createTaskQuery()
                        .processInstanceBusinessKey(caseId.toString())
                        .singleResult();
        return Optional.ofNullable(task).map(Task::getTaskDefinitionKey);
    }

    private Task activeInvestigateTask(UUID caseId) {
        return taskService
                .createTaskQuery()
                .processInstanceBusinessKey(caseId.toString())
                .taskDefinitionKey(INVESTIGATE_TASK)
                .singleResult();
    }
}
