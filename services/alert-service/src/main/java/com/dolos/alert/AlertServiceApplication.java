package com.dolos.alert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for alert-service (Phase 1E) — consumes {@code RiskScored}, and when a score crosses
 * the configured threshold persists an {@code Alert}, publishes {@code AlertRaised}, and serves a
 * paged, risk-sorted {@code GET /api/alerts}. Alert creation is idempotent on the transaction id.
 */
@SpringBootApplication
public class AlertServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertServiceApplication.class, args);
    }
}
