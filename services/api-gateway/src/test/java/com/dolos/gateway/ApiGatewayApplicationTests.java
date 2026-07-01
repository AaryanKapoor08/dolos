package com.dolos.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test (Phase 5B): the gateway context starts. This proves the reactive stack wires up cleanly —
 * the Spring Cloud Gateway route table, the Resilience4j circuit-breaker filters, and the reactive
 * resource-server SecurityWebFilterChain (from dolos-security's shared realm-role converter) all resolve
 * together under the Spring Cloud BOM. Eureka registration is disabled so the test needs no registry;
 * the JWT decoder is built lazily from jwk-set-uri, so no Keycloak is needed to boot.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"eureka.client.enabled=false", "spring.cloud.config.enabled=false"})
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {}
}
