package com.dolos.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Entry point for config-server (Phase 5A) — the Spring Cloud Config server.
 *
 * <p>{@link EnableConfigServer} exposes the centralized configuration in {@code deploy/config-repo}
 * over HTTP (e.g. {@code GET /alert-service/default} returns alert-service's merged config). Backed
 * by the {@code native} filesystem profile rather than a remote git repo, so the same folder serves
 * both host and container with no extra moving parts. Clients import it with
 * {@code spring.config.import: optional:configserver:...} so they still boot if this server is down.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
