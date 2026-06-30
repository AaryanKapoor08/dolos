package com.dolos.copilot.agent;

import com.dolos.copilot.agent.InvestigateAlertService.InvestigationResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The investigate-alert agent endpoint (Phase 4E). {@code POST /api/copilot/investigate} runs the full
 * investigation loop for an alert — gather transactions + graph/ring + regulations, synthesize a SAR
 * draft, write it to MinIO, and link it to the alert's case as evidence — and returns the draft plus
 * where it landed and a traceable step log.
 */
@RestController
@RequestMapping("/api/copilot")
public class InvestigateController {

    private final InvestigateAlertService investigateService;

    public InvestigateController(InvestigateAlertService investigateService) {
        this.investigateService = investigateService;
    }

    @PostMapping("/investigate")
    public InvestigationResult investigate(@Valid @RequestBody InvestigateRequest request) {
        return investigateService.investigate(request.alertId());
    }

    /** The alert to investigate. */
    public record InvestigateRequest(@NotNull UUID alertId) {}
}
