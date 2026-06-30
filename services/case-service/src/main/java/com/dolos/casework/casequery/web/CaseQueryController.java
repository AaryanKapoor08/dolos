package com.dolos.casework.casequery.web;

import com.dolos.casework.GlobalExceptionHandler.NotFoundException;
import com.dolos.casework.casequery.CaseDetails;
import com.dolos.casework.casequery.CaseQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read-side REST API of case-service (Phase 3C). Serves the CQRS read model: a case's current
 * state plus its full event timeline. Writes go through the command controller (Phase 3B) — the two
 * sides are deliberately separate.
 */
@RestController
@RequestMapping("/api/cases")
public class CaseQueryController {

    private final CaseQueryService queryService;

    public CaseQueryController(CaseQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{id}")
    public CaseDetails get(@PathVariable("id") UUID id) {
        return queryService
                .findById(id)
                .orElseThrow(() -> new NotFoundException("case " + id + " not found"));
    }

    @GetMapping
    public List<CaseDetails> list() {
        return queryService.findAll();
    }
}
