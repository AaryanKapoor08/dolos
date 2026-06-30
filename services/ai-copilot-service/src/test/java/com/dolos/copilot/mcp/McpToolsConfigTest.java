package com.dolos.copilot.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.dolos.copilot.tools.PlatformProperties;
import com.dolos.copilot.tools.PlatformTools;
import com.dolos.copilot.tools.ServiceTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.client.RestClient;

/**
 * Unit test for the Phase 4F MCP tool exposure. Reflects over the real {@link PlatformTools} (so its
 * {@code @Tool} annotations are read) and asserts the {@link ToolCallbackProvider} the MCP server will
 * publish lists exactly the four platform tools — and not the internal 4E agent-support helpers. The
 * live MCP handshake (an external client listing + calling tools) is Docker-verified.
 */
class McpToolsConfigTest {

    @Test
    void exposesThePlatformToolsAndNotTheAgentHelpers() {
        PlatformProperties props =
                new PlatformProperties(
                        "http://transaction-service:8081",
                        "http://alert-service:8084",
                        "http://graph-service:8085",
                        "http://case-service:8086",
                        25,
                        new PlatformProperties.Keycloak("http://keycloak/token", "dolos-copilot", "secret"));
        PlatformTools tools =
                new PlatformTools(
                        RestClient.builder(), props, mock(ServiceTokenProvider.class), new ObjectMapper());

        ToolCallbackProvider provider = new McpToolsConfig().platformToolCallbackProvider(tools);
        var names =
                Arrays.stream(provider.getToolCallbacks())
                        .map(ToolCallback::getToolDefinition)
                        .map(def -> def.name())
                        .toList();

        assertThat(names)
                .containsExactlyInAnyOrder(
                        "getTransactionHistory", "getRecentAlerts", "getCaseDetails", "runGraphQuery");
    }
}
