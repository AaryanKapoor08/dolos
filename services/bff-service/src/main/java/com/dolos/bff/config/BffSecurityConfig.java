package com.dolos.bff.config;

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
 * bff-service access rules (Phase 5D). Declaring this reactive {@link SecurityWebFilterChain} overrides
 * the shared default from {@code dolos-security} (whose {@code @ConditionalOnMissingBean} backs off)
 * while reusing its Keycloak realm-role converter.
 *
 * <p>The GraphiQL explorer UI ({@code /graphiql/**}) and actuator probes are open; CORS preflight
 * ({@code OPTIONS}) is open so a browser can preflight a cross-origin call. Every {@code /graphql}
 * request needs a valid Keycloak JWT — the token is then relayed to each downstream service (see
 * {@code WebClientConfig}), so authorization is enforced consistently at every hop.
 */
@Configuration
public class BffSecurityConfig {

    @Bean
    public SecurityWebFilterChain bffSecurityFilterChain(
            ServerHttpSecurity http, Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtConverter) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(
                        exchange ->
                                exchange
                                        .pathMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .pathMatchers(
                                                "/graphiql/**",
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                // Prometheus scrape target (Phase 6A) — open internally.
                                                "/actuator/prometheus")
                                        .permitAll()
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }
}
