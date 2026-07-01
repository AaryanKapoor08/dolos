package com.dolos.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for api-gateway (Phase 5B) — the platform's single secured edge.
 *
 * <p>A reactive Spring Cloud Gateway: it registers with Eureka (so it can route by {@code
 * lb://service-name}), fronts every business service under {@code /api/**} (plus the {@code /ingest}
 * path), validates the caller's Keycloak JWT once at the edge, relays that token downstream, and trips a
 * Resilience4j circuit breaker to a local 503 fallback when a route's service is unhealthy. Routes and
 * breaker settings live in {@code application.yml}; the edge access rules live in {@link
 * GatewaySecurityConfig}.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
