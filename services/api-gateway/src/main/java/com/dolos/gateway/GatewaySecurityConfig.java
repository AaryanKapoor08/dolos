package com.dolos.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * The gateway's edge access rules (Phase 5B). Declaring this {@link SecurityWebFilterChain} bean
 * overrides the default one from {@code dolos-security}'s reactive auto-configuration (whose {@code
 * @ConditionalOnMissingBean} then backs off) while still reusing its shared Keycloak realm-role
 * converter, injected here.
 *
 * <p>Rules: actuator probes and the circuit-breaker fallbacks are open; {@code /ingest/**} is open
 * (ingestion accepts NiFi's unsecured feed and enforces its own read-side auth); every {@code /api/**}
 * exchange requires a valid JWT — an unauthenticated call is rejected with 401 here at the edge before
 * it ever reaches a service. Validated requests carry their bearer token downstream (Gateway forwards
 * the {@code Authorization} header by default), so each service re-validates independently.
 */
@Configuration
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain gatewaySecurityFilterChain(
            ServerHttpSecurity http, Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtConverter) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(
                        exchange ->
                                exchange
                                        // CORS preflight (5E): unauthenticated OPTIONS must pass so the
                                        // browser can complete the preflight before the real request.
                                        .pathMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .pathMatchers(
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                // Prometheus scrape target (Phase 6A) — open internally.
                                                "/actuator/prometheus",
                                                "/fallback/**",
                                                "/ingest/**",
                                                // Live push (5C): the browser can't set Authorization on
                                                // the WS upgrade, so the handshake is open at the edge.
                                                "/ws/**",
                                                // GraphiQL explorer UI (5D) is static; its queries to
                                                // /graphql still carry a token and are authenticated.
                                                "/graphiql/**")
                                        .permitAll()
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }
}
