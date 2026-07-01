package com.dolos.bff.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * The WebClient the BFF resolvers use to reach the business services (Phase 5D). Two things make it
 * work behind Eureka + Keycloak:
 *
 * <ul>
 *   <li>{@link LoadBalanced} on the {@link WebClient.Builder} so {@code lb://alert-service/...} URIs
 *       resolve to a live instance via the registry.
 *   <li>A {@linkplain #bearerTokenRelay() bearer-token relay} filter that lifts the caller's validated
 *       JWT out of the reactive security context and forwards it as {@code Authorization: Bearer ...},
 *       so each downstream re-validates the SAME user — the gateway's token relay, continued one hop in.
 * </ul>
 */
@Configuration
public class WebClientConfig {

    /** Load-balanced builder: {@code @LoadBalanced} wires the reactive lb:// resolver into it. */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    /** The resolver-facing client: load-balanced + relays the caller's token on every request. */
    @Bean
    public WebClient dolosWebClient(WebClient.Builder loadBalancedWebClientBuilder) {
        return loadBalancedWebClientBuilder.filter(bearerTokenRelay()).build();
    }

    /**
     * Copies the current request's authenticated JWT onto the outbound request. If there is no JWT in
     * context (e.g. an unauthenticated probe), the request goes out unchanged rather than failing here —
     * the downstream then rejects it, keeping authorization decisions at the resource servers.
     */
    static ExchangeFilterFunction bearerTokenRelay() {
        return (request, next) ->
                ReactiveSecurityContextHolder.getContext()
                        .map(ctx -> ctx.getAuthentication())
                        .filter(auth -> auth instanceof JwtAuthenticationToken)
                        .map(auth -> ((JwtAuthenticationToken) auth).getToken().getTokenValue())
                        .map(
                                token ->
                                        ClientRequest.from(request)
                                                .headers(h -> h.setBearerAuth(token))
                                                .build())
                        .defaultIfEmpty(request)
                        .flatMap(next::exchange);
    }
}
