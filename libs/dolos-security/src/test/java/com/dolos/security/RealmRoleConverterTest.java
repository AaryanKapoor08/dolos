package com.dolos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Phase 3F — the Keycloak realm-role -> {@code ROLE_*} authority mapping. */
class RealmRoleConverterTest {

    private final RealmRoleConverter converter = new RealmRoleConverter();

    private static Jwt jwtWith(Map<String, Object> claims) {
        Jwt.Builder builder =
                Jwt.withTokenValue("token")
                        .header("alg", "RS256")
                        .issuedAt(Instant.EPOCH)
                        .expiresAt(Instant.EPOCH.plusSeconds(300))
                        .subject("user");
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void mapsRealmRolesToPrefixedAuthorities() {
        Jwt jwt = jwtWith(Map.of("realm_access", Map.of("roles", List.of("ANALYST", "SENIOR_ANALYST"))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ANALYST", "ROLE_SENIOR_ANALYST");
    }

    @Test
    void yieldsNoAuthoritiesWhenRealmAccessMissing() {
        assertThat(converter.convert(jwtWith(Map.of("scope", "openid")))).isEmpty();
    }
}
