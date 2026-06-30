package com.dolos.copilot.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit test for the Phase 4D platform tools. A {@link MockRestServiceServer} bound to the shared
 * {@code RestClient.Builder} stands in for the downstream services, so this verifies each tool's REST
 * contract (URL, the case-service bearer token, alert-envelope stripping, 404 handling) without a live
 * platform or a model. The real tool-calling loop is Docker-verified.
 */
@ExtendWith(MockitoExtension.class)
class PlatformToolsTest {

    private static final PlatformProperties PROPS =
            new PlatformProperties(
                    "http://transaction-service:8081",
                    "http://alert-service:8084",
                    "http://graph-service:8085",
                    "http://case-service:8086",
                    25,
                    new PlatformProperties.Keycloak("http://keycloak/token", "dolos-copilot", "secret"));

    @Mock private ServiceTokenProvider tokens;

    private MockRestServiceServer server;
    private PlatformTools tools;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        // Bind the mock to the builder; PlatformTools clones it per service, inheriting the mock factory.
        server = MockRestServiceServer.bindTo(builder).build();
        tools = new PlatformTools(builder, PROPS, tokens, new ObjectMapper());
    }

    @Test
    void getTransactionHistory_callsByAccountWithLimit() {
        server.expect(requestTo("http://transaction-service:8081/api/transactions?accountId=ACC-1&limit=25"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess("[{\"id\":\"t1\",\"amount\":250}]", MediaType.APPLICATION_JSON));

        String result = tools.getTransactionHistory("ACC-1");

        assertThat(result).contains("\"id\":\"t1\"");
        server.verify();
    }

    @Test
    void getRecentAlerts_stripsPagingEnvelope() {
        server.expect(requestTo("http://alert-service:8084/api/alerts"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                "{\"content\":[{\"alertId\":\"a1\",\"severity\":\"HIGH\"}],\"page\":{\"size\":20}}",
                                MediaType.APPLICATION_JSON));

        String result = tools.getRecentAlerts();

        assertThat(result).contains("\"alertId\":\"a1\"").doesNotContain("\"page\"");
        server.verify();
    }

    @Test
    void getCaseDetails_sendsBearerToken() {
        when(tokens.accessToken()).thenReturn("test-token");
        server.expect(requestTo("http://case-service:8086/api/cases/c-123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"caseId\":\"c-123\",\"status\":\"OPEN\"}", MediaType.APPLICATION_JSON));

        String result = tools.getCaseDetails("c-123");

        assertThat(result).contains("\"status\":\"OPEN\"");
        server.verify();
    }

    @Test
    void getCaseDetails_whenNotFound_returnsFriendlyMessage() {
        when(tokens.accessToken()).thenReturn("test-token");
        server.expect(requestTo("http://case-service:8086/api/cases/missing"))
                .andRespond(withResourceNotFound());

        String result = tools.getCaseDetails("missing");

        assertThat(result).isEqualTo("No case found with id missing.");
        server.verify();
    }

    @Test
    void runGraphQuery_returnsNeighborhoodIncludingRingFlag() {
        server.expect(requestTo("http://graph-service:8085/api/graph/account/ACC-1/neighborhood"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                "{\"accountId\":\"ACC-1\",\"inRing\":true,\"rings\":[\"RING-A-B\"]}",
                                MediaType.APPLICATION_JSON));

        String result = tools.runGraphQuery("ACC-1");

        assertThat(result).contains("\"inRing\":true").contains("RING-A-B");
        server.verify();
    }
}
