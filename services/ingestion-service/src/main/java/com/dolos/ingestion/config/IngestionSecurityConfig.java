package com.dolos.ingestion.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Reactive edge security for ingestion-service (Phase 5B). ingestion is WebFlux, so it is secured by the
 * shared {@code DolosReactiveSecurityAutoConfiguration} (which supplies the Keycloak realm-role
 * converter); this chain overrides that module's default one (its {@code @ConditionalOnMissingBean} then
 * backs off) to keep the {@code /ingest} path OPEN.
 *
 * <p>{@code /ingest} must stay unauthenticated because NiFi (the bulk CSV feed) POSTs to it anonymously
 * over HTTP - a local-dev bootstrap convenience. Everything else requires a valid JWT.
 *
 * <p>Guarded by {@code @ConditionalOnProperty} on the jwk-set-uri: with no Keycloak configured (e.g. the
 * slice-e2e in-process boot, which disables file config) this chain is absent, so ingestion runs open.
 */
@Configuration
@ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri")
public class IngestionSecurityConfig {

    @Bean
    public SecurityWebFilterChain ingestionSecurityFilterChain(
            ServerHttpSecurity http, Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtConverter) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(
                        exchange ->
                                exchange
                                        .pathMatchers(
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/actuator/info")
                                        .permitAll()
                                        // NiFi's unsecured bulk feed target - must stay open.
                                        .pathMatchers("/ingest/**")
                                        .permitAll()
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }
}
