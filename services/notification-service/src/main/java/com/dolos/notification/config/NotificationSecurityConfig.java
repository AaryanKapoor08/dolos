package com.dolos.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * notification-service access rules (Phase 5C). Declaring this {@link SecurityFilterChain} overrides
 * the shared default from {@code dolos-security} (whose {@code @ConditionalOnMissingBean} backs off)
 * while reusing its {@link JwtAuthenticationConverter} bean.
 *
 * <p>The WebSocket handshake at {@code /ws/**} is left open: a browser can't attach an {@code
 * Authorization} header to the WS upgrade, so — like ingestion's {@code /ingest} and the copilot's MCP
 * SSE — this path is permitted and the gateway forwards it as-is. Everything else (there is no other
 * public surface beyond the actuator probes) still requires a valid Keycloak JWT, keeping the service a
 * resource server. Stateless, no CSRF (token API + a WS upgrade, not a browser form).
 */
@Configuration
public class NotificationSecurityConfig {

    @Bean
    public SecurityFilterChain notificationSecurityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/ws/**",
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                // Prometheus scrape target (Phase 6A) — open internally.
                                                "/actuator/prometheus")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
