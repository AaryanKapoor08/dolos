package com.dolos.alert.api;

import com.dolos.alert.api.dto.AlertResponse;
import com.dolos.alert.service.AlertService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API for alerts. Risk-sorted by default (highest score first) so an analyst's queue surfaces
 * the most dangerous alerts at the top; the page is overridable via the standard {@code page}/
 * {@code size}/{@code sort} query params.
 *
 * <p>Goes through {@link AlertService} rather than touching the repository directly, keeping the
 * controller→service→repository layering (enforced by ArchUnit in Phase 1F).
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    public PagedModel<AlertResponse> list(
            @PageableDefault(size = 20, sort = "score", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        Page<AlertResponse> page = service.findAlerts(pageable);
        return new PagedModel<>(page);
    }
}
