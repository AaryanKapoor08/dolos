package com.dolos.copilot.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tool-calling copilot endpoint (Phase 4D). {@code POST /api/copilot/agent} answers a question by
 * letting the model invoke platform tools (transaction history, alerts, graph/ring, case details) and
 * grounding its reply in real data — e.g. "show recent transactions for ACC-1001 and whether it's in a
 * ring".
 */
@RestController
@RequestMapping("/api/copilot")
public class AgentController {

    private final CopilotAgentService agentService;

    public AgentController(CopilotAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/agent")
    public AgentResponse agent(@Valid @RequestBody AgentRequest request) {
        return new AgentResponse(agentService.run(request.message()));
    }

    /** A question the copilot may answer using platform tools. */
    public record AgentRequest(@NotBlank String message) {}

    /** The model's answer, grounded in tool results. */
    public record AgentResponse(String reply) {}
}
