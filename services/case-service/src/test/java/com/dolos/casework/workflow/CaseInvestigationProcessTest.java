package com.dolos.casework.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.dolos.casework.casecmd.CaseCommandService;
import com.dolos.casework.workflow.delegate.AssignCaseDelegate;
import com.dolos.casework.workflow.delegate.CloseCaseDelegate;
import com.dolos.casework.workflow.delegate.EscalateCaseDelegate;
import com.dolos.casework.workflow.delegate.FileReportDelegate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Phase 3D — proves the {@code case-investigation} BPMN actually drives the case lifecycle: deploying
 * the real process definition into a standalone in-memory (H2) Flowable engine and walking it must
 * dispatch the right Axon commands, in order, on both gateway branches. The {@link CaseCommandService}
 * is mocked, so this is a fast, Postgres-free unit test of the workflow wiring; the "process state
 * matches the event-sourced state" half of the DoD is verified end-to-end in Docker.
 */
class CaseInvestigationProcessTest {

    private ProcessEngine engine;
    private CaseCommandService commands;
    private CaseWorkflowService workflow;

    @BeforeEach
    void setUp() {
        commands = mock(CaseCommandService.class);

        Map<Object, Object> beans = new HashMap<>();
        beans.put("assignCaseDelegate", new AssignCaseDelegate(commands));
        beans.put("escalateCaseDelegate", new EscalateCaseDelegate(commands));
        beans.put("fileReportDelegate", new FileReportDelegate(commands));
        beans.put("closeCaseDelegate", new CloseCaseDelegate(commands));

        ProcessEngineConfiguration config =
                ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
        // Unique in-mem DB per test so engines never share H2 state.
        config.setJdbcUrl("jdbc:h2:mem:flowable-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=1000");
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(false);
        config.setBeans(beans);
        engine = config.buildProcessEngine();

        engine.getRepositoryService()
                .createDeployment()
                .addClasspathResource("processes/case-investigation.bpmn20.xml")
                .deploy();

        RuntimeService runtimeService = engine.getRuntimeService();
        TaskService taskService = engine.getTaskService();
        workflow = new CaseWorkflowService(runtimeService, taskService);
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void escalateBranch_drivesAssignEscalateFileReportClose() {
        UUID caseId = UUID.randomUUID();

        workflow.startInvestigation(caseId, "alice", "tester");

        // Assign runs immediately; the process then parks on the analyst's user task.
        verify(commands).assign(caseId, "alice", "tester");
        assertThat(workflow.activeTask(caseId)).contains("investigateTask");

        workflow.completeInvestigation(
                caseId, true, "structuring confirmed", "SAR-2026-1", "filed and escalated", "tester");

        // Completing the user task runs escalate -> file report -> close synchronously, in order.
        InOrder ordered = inOrder(commands);
        ordered.verify(commands).assign(caseId, "alice", "tester");
        ordered.verify(commands).escalate(caseId, "structuring confirmed", "tester");
        ordered.verify(commands).fileReport(caseId, "SAR-2026-1", "tester");
        ordered.verify(commands).closeCase(caseId, "filed and escalated", "tester");
        verifyNoMoreInteractions(commands);

        assertThat(workflow.activeTask(caseId)).isEmpty();
    }

    @Test
    void closeBranch_skipsEscalateAndReport() {
        UUID caseId = UUID.randomUUID();

        workflow.startInvestigation(caseId, "bob", "tester");
        workflow.completeInvestigation(caseId, false, null, null, "no action — false positive", "tester");

        InOrder ordered = inOrder(commands);
        ordered.verify(commands).assign(caseId, "bob", "tester");
        ordered.verify(commands).closeCase(caseId, "no action — false positive", "tester");
        verifyNoMoreInteractions(commands); // never escalated, never filed a report

        assertThat(workflow.activeTask(caseId)).isEmpty();
    }
}
