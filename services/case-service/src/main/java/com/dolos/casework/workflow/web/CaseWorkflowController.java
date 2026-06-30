package com.dolos.casework.workflow.web;

import com.dolos.casework.workflow.CaseWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry points for the BPMN investigation workflow (Phase 3D). Distinct from the command API
 * under {@code /api/cases/{id}} (which mutates the aggregate directly): these drive the case
 * <em>through the Flowable process</em>, whose service tasks dispatch the same Axon commands. The
 * {@code actor} fields default to {@code "system"} until Keycloak supplies the principal (Phase 3F).
 */
@RestController
@RequestMapping("/api/cases/{id}/workflow")
public class CaseWorkflowController {

    private static final String SYSTEM = "system";

    private final CaseWorkflowService workflow;

    public CaseWorkflowController(CaseWorkflowService workflow) {
        this.workflow = workflow;
    }

    /** Starts the investigation process for an already-opened case; runs assign, then waits to investigate. */
    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> start(
            @PathVariable("id") UUID id, @Valid @RequestBody StartRequest request) {
        String processInstanceId =
                workflow.startInvestigation(id, request.assignee(), actor(request.actor()));
        return ResponseEntity.ok(Map.of("processInstanceId", processInstanceId));
    }

    /** Completes the analyst's investigate task, routing through the escalate-vs-close gateway. */
    @PostMapping("/investigate")
    public ResponseEntity<Void> investigate(
            @PathVariable("id") UUID id, @Valid @RequestBody InvestigateRequest request) {
        workflow.completeInvestigation(
                id,
                request.escalate(),
                request.reason(),
                request.reportReference(),
                request.resolution(),
                actor(request.actor()));
        return ResponseEntity.noContent().build();
    }

    /** The user task the process is currently waiting on (empty once the process has ended). */
    @GetMapping
    public ResponseEntity<Map<String, String>> status(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(
                Map.of("activeTask", workflow.activeTask(id).orElse("")));
    }

    private static String actor(String supplied) {
        return supplied == null || supplied.isBlank() ? SYSTEM : supplied;
    }

    // --- Request bodies -------------------------------------------------------------------------

    public record StartRequest(@NotBlank String assignee, String actor) {}

    public record InvestigateRequest(
            boolean escalate, String reason, String reportReference, String resolution, String actor) {}
}
