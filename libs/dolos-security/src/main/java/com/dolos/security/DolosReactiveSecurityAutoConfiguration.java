package com.dolos.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * The reactive counterpart of {@link DolosSecurityAutoConfiguration} (Phase 5B) for the platform's
 * WebFlux edges — api-gateway and ingestion-service. It shares the same Keycloak {@link
 * RealmRoleConverter} (realm roles → {@code ROLE_*}) so authorization is identical to the servlet
 * services, adapted into the reactive {@link ReactiveJwtAuthenticationConverter}.
 *
 * <p>It exposes that converter as a bean and installs a sensible default {@link SecurityWebFilterChain}
 * (stateless, actuator health/info open, every other exchange authenticated). A reactive service that
 * needs different rules — the gateway permits {@code /api/**} only with a token but leaves {@code
 * /ingest} and fallbacks open; ingestion leaves {@code /ingest} open — declares its own {@code
 * SecurityWebFilterChain} bean, which suppresses this default via {@link ConditionalOnMissingBean} while
 * still reusing the shared converter.
 */
@AutoConfiguration
@ConditionalOnClass(SecurityWebFilterChain.class)
@ConditionalOnWebApplication(type = Type.REACTIVE)
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class DolosReactiveSecurityAutoConfiguration {

    /**
     * The shared reactive JWT authentication converter: maps Keycloak realm roles to {@code ROLE_*}
     * authorities, reusing the same {@link RealmRoleConverter} as the servlet chain. Exposed as a bean
     * so a service's own {@link SecurityWebFilterChain} can wire it into {@code oauth2ResourceServer}.
     *
     * <p>Intentionally NOT {@code @ConditionalOnMissingBean}: a WebFlux context already holds many raw
     * {@code Converter} beans (the conversion service), and that condition would spuriously match one and
     * back off, leaving the chain with nothing to inject. The specific parameterized type is unique here.
     */
    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> dolosReactiveJwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new ReactiveJwtGrantedAuthoritiesConverterAdapter(new RealmRoleConverter()));
        return converter;
    }

    /**
     * The default reactive filter chain: stateless, actuator probes open, everything else needs a valid
     * JWT. Skipped when a service declares its own {@link SecurityWebFilterChain}.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityWebFilterChain.class)
    public SecurityWebFilterChain dolosReactiveSecurityFilterChain(
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
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }
}
