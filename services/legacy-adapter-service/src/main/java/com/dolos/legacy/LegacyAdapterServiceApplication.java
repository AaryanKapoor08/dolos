package com.dolos.legacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * legacy-adapter-service (Phase 6D): the code-defined integration edge. Boots a Camel context whose
 * single {@code LegacyFeedRouteBuilder} route polls a bind-mounted inbox for legacy fixed-width partner
 * files and translates each record into the canonical {@code TransactionReceived} event on Redpanda —
 * the same pipeline entry point as the reactive ingestion-service and the visual NiFi flow (Phase 1G).
 *
 * <p>camel-spring-boot auto-starts the context and discovers the {@code RouteBuilder} bean; there is no
 * imperative bootstrap here beyond the standard Spring Boot launch.
 */
@SpringBootApplication
public class LegacyAdapterServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegacyAdapterServiceApplication.class, args);
    }
}
