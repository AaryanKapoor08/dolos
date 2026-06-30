package com.dolos.casework.casecmd.web;

import com.dolos.casework.casecmd.CaseCommandService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
 * <p>Secured as an OAuth2 resource server (Phase 3F): every call needs a valid Keycloak JWT, and the
 * actor recorded on each event is the authenticated user ({@code preferred_username}). Escalating and
 * filing a report are senior-only, enforced with {@code @PreAuthorize} method security.
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
    public ResponseEntity<Map<String, UUID>> open(
            @Valid @RequestBody OpenRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID caseId =
                commandService.openCase(
                        request.alertId(),
                        request.accountId(),
                        request.score(),
                        actor(request.openedBy(), jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("caseId", caseId));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Void> assign(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AssignRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        commandService.assign(id, request.assignee(), actor(request.assignedBy(), jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/evidence")
    public ResponseEntity<Void> addEvidence(
            @PathVariable("id") UUID id,
            @Valid @RequestBody EvidenceRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        commandService.addEvidence(id, request.note(), actor(request.addedBy(), jwt));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('SENIOR_ANALYST')")
    @PostMapping("/{id}/escalate")
    public ResponseEntity<Void> escalate(
            @PathVariable("id") UUID id,
            @Valid @RequestBody EscalateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        commandService.escalate(id, request.reason(), actor(request.escalatedBy(), jwt));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('SENIOR_ANALYST')")
    @PostMapping("/{id}/report")
    public ResponseEntity<Void> fileReport(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReportRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        commandService.fileReport(id, request.reportReference(), actor(request.filedBy(), jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CloseRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        commandService.closeCase(id, request.resolution(), actor(request.closedBy(), jwt));
        return ResponseEntity.noContent().build();
    }

    /** The acting user is the authenticated principal; fall back to a supplied name, then "system". */
    private static String actor(String supplied, Jwt jwt) {
        if (jwt != null) {
            String username = jwt.getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
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
