package com.dolos.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Entry point for discovery-server (Phase 5A) — the Netflix Eureka service registry.
 *
 * <p>{@link EnableEurekaServer} stands up the registry and its dashboard (served at the context
 * root, http://localhost:8761). Every platform service registers here as a Eureka client, which
 * lets the API gateway (Phase 5B) route to them by logical name via {@code lb://service-name}.
 * This server registers nothing itself (see application.yml).
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
