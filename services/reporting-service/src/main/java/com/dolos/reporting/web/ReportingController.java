package com.dolos.reporting.web;

import com.dolos.reporting.ReportLauncher;
import com.dolos.reporting.ReportLauncher.LaunchResult;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator surface for the reporting pipeline (Phase 6B). Secured like every service by
 * {@code dolos-security} (a valid Keycloak JWT is required):
 *
 * <ul>
 *   <li>{@code POST /api/reports/sar/run?date=YYYY-MM-DD} — file (or restart) the SAR/STR run for a
 *       business date (defaults to today, UTC). The nightly scheduler files yesterday automatically.</li>
 *   <li>{@code GET /api/reports/filed?date=YYYY-MM-DD} — list the reports filed (from
 *       {@code reporting.filed_report}); no date filter returns the most recent 100.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reports")
public class ReportingController {

    private final ReportLauncher launcher;
    private final JdbcTemplate jdbc;

    public ReportingController(ReportLauncher launcher, JdbcTemplate jdbc) {
        this.launcher = launcher;
        this.jdbc = jdbc;
    }

    @PostMapping("/sar/run")
    public LaunchResult run(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        LocalDate businessDate = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        return launcher.fileFor(businessDate);
    }

    @GetMapping("/filed")
    public ResponseEntity<List<Map<String, Object>>> filed(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        String base =
                "SELECT report_ref, alert_id, account_id, report_type, score, business_date,"
                        + " object_pointer, filed_at FROM reporting.filed_report";
        List<Map<String, Object>> rows =
                date != null
                        ? jdbc.queryForList(
                                base + " WHERE business_date = ? ORDER BY filed_at DESC", date)
                        : jdbc.queryForList(base + " ORDER BY filed_at DESC LIMIT 100");
        return ResponseEntity.ok(rows);
    }
}
