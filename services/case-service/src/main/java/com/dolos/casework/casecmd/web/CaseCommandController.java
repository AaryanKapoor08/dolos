package com.dolos.casework.casecmd.web;

import com.dolos.casework.casecmd.CaseCommandService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The command-side REST API of case-service (Phase 3B). Every endpoint dispatches an Axon command via
 * {@link CaseCommandService}; the read side ({@code GET}) is served separately by the query module
 * (Phase 3C), keeping the write and read APIs cleanly split (CQRS).
 *
 * <p>The {@code *By} actor fields default to {@code "system"} when omitted so the API is easy to drive
 * by hand; once Keycloak lands (Phase 3F) they will come from the authenticated principal.
 */
@RestController
@RequestMapping("/api/cases")
public class CaseCommandController {

    private static final String SYSTEM = "system";

    private final CaseCommandService commandService;

    public CaseCommandController(CaseCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> open(@Valid @RequestBody OpenRequest request) {
        UUID caseId =
                commandService.openCase(
                        request.alertId(),
                        request.accountId(),
                        request.score(),
                        actor(request.openedBy()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("caseId", caseId));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Void> assign(
            @PathVariable("id") UUID id, @Valid @RequestBody AssignRequest request) {
        commandService.assign(id, request.assignee(), actor(request.assignedBy()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/evidence")
    public ResponseEntity<Void> addEvidence(
            @PathVariable("id") UUID id, @Valid @RequestBody EvidenceRequest request) {
        commandService.addEvidence(id, request.note(), actor(request.addedBy()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/escalate")
    public ResponseEntity<Void> escalate(
            @PathVariable("id") UUID id, @Valid @RequestBody EscalateRequest request) {
        commandService.escalate(id, request.reason(), actor(request.escalatedBy()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<Void> fileReport(
            @PathVariable("id") UUID id, @Valid @RequestBody ReportRequest request) {
        commandService.fileReport(id, request.reportReference(), actor(request.filedBy()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(
            @PathVariable("id") UUID id, @Valid @RequestBody CloseRequest request) {
        commandService.closeCase(id, request.resolution(), actor(request.closedBy()));
        return ResponseEntity.noContent().build();
    }

    private static String actor(String supplied) {
        return supplied == null || supplied.isBlank() ? SYSTEM : supplied;
    }

    // --- Request bodies ---------------------------------------------------------------------------

    public record OpenRequest(
            UUID alertId, @NotBlank String accountId, @Min(0) int score, String openedBy) {}

    public record AssignRequest(@NotBlank String assignee, String assignedBy) {}

    public record EvidenceRequest(@NotBlank String note, String addedBy) {}

    public record EscalateRequest(@NotBlank String reason, String escalatedBy) {}

    public record ReportRequest(@NotBlank String reportReference, String filedBy) {}

    public record CloseRequest(@NotBlank String resolution, String closedBy) {}
}
