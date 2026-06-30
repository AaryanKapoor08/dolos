package com.dolos.copilot.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Obtains (and caches) a Keycloak service token via the OAuth2 <b>client-credentials</b> grant, so the
 * copilot can call the secured case-service (Phase 3F) without a human's interactive login. The
 * {@code dolos-copilot} confidential client has {@code serviceAccountsEnabled}; its service account is
 * granted ANALYST/SENIOR_ANALYST so a tool call carries the roles case-service expects.
 *
 * <p>The token is cached until shortly before it expires (a 30s safety margin) and refreshed on demand,
 * so a burst of tool calls hits the token endpoint at most once per token lifetime.
 */
@Component
public class ServiceTokenProvider {

    /** Refresh this long before the stated expiry, to avoid sending an about-to-expire token. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final RestClient http;
    private final PlatformProperties.Keycloak cfg;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(PlatformProperties props, RestClient.Builder builder) {
        this.cfg = props.keycloak();
        this.http = builder.build();
    }

    /** A valid bearer token for the case-service, fetching a fresh one only when the cache is stale. */
    public synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
            return cachedToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", cfg.clientId());
        form.add("client_secret", cfg.clientSecret());

        TokenResponse token =
                http.post()
                        .uri(cfg.tokenUri())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(TokenResponse.class);

        if (token == null || token.accessToken() == null) {
            throw new IllegalStateException("Keycloak returned no access_token for client-credentials grant");
        }
        cachedToken = token.accessToken();
        expiresAt = Instant.now().plusSeconds(token.expiresIn()).minus(EXPIRY_MARGIN);
        return cachedToken;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {}
}
