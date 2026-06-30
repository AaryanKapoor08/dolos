package com.dolos.copilot.qa;

import com.dolos.copilot.qa.CopilotAskService.GroundedAnswer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The grounded-answer endpoint (Phase 4C). {@code POST /api/copilot/ask} answers a regulation question
 * from the RAG corpus with citations, and declines questions the corpus does not cover — unlike the raw
 * {@code /chat} endpoint, which is free-form and ungrounded.
 */
@RestController
@RequestMapping("/api/copilot")
public class AskController {

    private final CopilotAskService askService;

    public AskController(CopilotAskService askService) {
        this.askService = askService;
    }

    @PostMapping("/ask")
    public GroundedAnswer ask(@Valid @RequestBody AskRequest request) {
        return askService.ask(request.question());
    }

    /** A regulation question to answer from the corpus. */
    public record AskRequest(@NotBlank String question) {}
}
