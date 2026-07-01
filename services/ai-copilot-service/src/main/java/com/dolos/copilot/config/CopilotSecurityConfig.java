package com.dolos.copilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ai-copilot resource-server security (Phase 5B). ai-copilot is a Spring MVC (servlet) app, so it is
 * secured by the shared {@code dolos-security} starter; this chain overrides that module's default one
 * (its {@code @ConditionalOnMissingBean} then backs off) to keep the MCP server's SSE endpoints OPEN.
 *
 * <p>{@code GET /sse} and {@code POST /mcp/message} (Phase 4F) let an external MCP client stream and call
 * the platform tools without an interactive Keycloak login - a local-dev convenience, mirroring how NiFi
 * hits ingestion's {@code /ingest} unauthenticated. Everything else (the {@code /api/copilot} chat, RAG,
 * agent endpoints) requires a valid JWT, reusing the shared realm-role {@link JwtAuthenticationConverter}.
 */
@Configuration
public class CopilotSecurityConfig {

    @Bean
    public SecurityFilterChain copilotSecurityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/actuator/info")
                                        .permitAll()
                                        // MCP server (4F): external tool clients connect unauthenticated.
                                        .requestMatchers("/sse", "/mcp/message", "/mcp/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
