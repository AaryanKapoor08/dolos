package com.dolos.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test (Phase 5A): the Eureka server context starts. A standalone registry neither registers
 * with nor fetches from itself (see application.yml), so the context loads without a peer — this just
 * proves the {@code @EnableEurekaServer} wiring + the Spring Cloud BOM resolve and boot cleanly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerApplicationTests {

    @Test
    void contextLoads() {}
}
