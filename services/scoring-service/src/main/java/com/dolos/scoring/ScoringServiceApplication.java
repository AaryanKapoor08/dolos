package com.dolos.scoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for scoring-service (Phase 1D) — consumes {@code TransactionReceived}, applies a
 * simple risk rule, and publishes {@code RiskScored}. Deliberately stateless in v0; Phase 2
 * replaces the in-code rule with a Kafka Streams + Drools topology.
 */
@SpringBootApplication
public class ScoringServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScoringServiceApplication.class, args);
    }
}
