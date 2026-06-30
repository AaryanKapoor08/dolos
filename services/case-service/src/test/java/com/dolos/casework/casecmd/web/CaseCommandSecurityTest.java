package com.dolos.casework.casecmd.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dolos.casework.casecmd.CaseCommandService;
import com.dolos.security.DolosSecurityAutoConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 3F — the access-control DoD on the command API: no token = 401, an {@code ANALYST} escalating =
 * 403, a {@code SENIOR_ANALYST} = success. Driven with mock JWTs (Spring Security test) so it runs
 * everywhere without a Keycloak; the realm role -> authority mapping is covered by
 * {@code RealmRoleConverterTest}, and the real-token path is Docker-verified against Keycloak.
 */
@WebMvcTest(CaseCommandController.class)
@Import(DolosSecurityAutoConfiguration.class)
class CaseCommandSecurityTest {

    private static final String ESCALATE = "/api/cases/" + UUID.randomUUID() + "/escalate";
    private static final String EVIDENCE = "/api/cases/" + UUID.randomUUID() + "/evidence";

    @Autowired private MockMvc mvc;

    // Required so the resource-server filter chain wires; the mock-JWT post-processors bypass decoding.
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private CaseCommandService commandService;

    private static SimpleGrantedAuthority role(String name) {
        return new SimpleGrantedAuthority("ROLE_" + name);
    }

    @Test
    void noToken_is401() throws Exception {
        mvc.perform(
                        post(ESCALATE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"ring confirmed\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void analystEscalating_is403() throws Exception {
        mvc.perform(
                        post(ESCALATE)
                                .with(jwt().authorities(role("ANALYST")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"ring confirmed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void seniorAnalystEscalating_succeeds() throws Exception {
        mvc.perform(
                        post(ESCALATE)
                                .with(jwt().authorities(role("ANALYST"), role("SENIOR_ANALYST")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"ring confirmed\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void analystCanDoNonSeniorActions() throws Exception {
        // A plain ANALYST is authenticated and allowed for non-gated actions (e.g. adding evidence).
        mvc.perform(
                        post(EVIDENCE)
                                .with(jwt().authorities(role("ANALYST")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"reviewed statements\"}"))
                .andExpect(status().isNoContent());
    }
}
