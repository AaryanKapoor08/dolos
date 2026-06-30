package com.dolos.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The Dolos shared resource-server auto-configuration (Phase 3F). Applying the {@code dolos-security}
 * starter and pointing a service at a Keycloak realm (via
 * {@code spring.security.oauth2.resourceserver.jwt.*}) is enough to secure every endpoint with a valid
 * JWT — no per-service security code.
 *
 * <p>It installs a stateless filter chain (no sessions, no CSRF — this is a token API), leaves the
 * actuator health/info probes open so Docker healthchecks still pass, requires authentication for
 * everything else, and validates bearer JWTs with realm roles mapped to {@code ROLE_*} authorities.
 * {@link EnableMethodSecurity} turns on {@code @PreAuthorize} so individual actions can be role-gated
 * (e.g. escalate / file-report = senior only).
 */
@AutoConfiguration
@ConditionalOnClass(SecurityFilterChain.class)
@EnableWebSecurity
@EnableMethodSecurity
public class DolosSecurityAutoConfiguration {

    @Bean
    public SecurityFilterChain dolosSecurityFilterChain(HttpSecurity http) throws Exception {
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
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter())));
        return http.build();
    }

    /** Builds the converter that turns Keycloak realm roles into {@code ROLE_*} authorities. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RealmRoleConverter());
        return converter;
    }
}
