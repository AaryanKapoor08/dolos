package com.dolos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test (Phase 5A): the Spring Cloud Config server context starts. Eureka registration is
 * disabled (no registry running in the test) and the native backend is pointed at the classpath so
 * the test does not depend on the deploy/config-repo working directory — this just proves the
 * {@code @EnableConfigServer} wiring + the Spring Cloud BOM resolve and boot cleanly.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "eureka.client.enabled=false",
            "spring.cloud.config.server.native.search-locations=classpath:/"
        })
class ConfigServerApplicationTests {

    @Test
    void contextLoads() {}
}
