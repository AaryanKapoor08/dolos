package com.dolos.copilot.mcp;

import com.dolos.copilot.tools.PlatformTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the platform tools over the Model Context Protocol (Phase 4F). The MCP server starter
 * (Spring MVC SSE transport) auto-configures the {@code /sse} + {@code /mcp/message} endpoints and
 * collects every {@link ToolCallbackProvider} bean; this one publishes the same {@code @Tool}-annotated
 * {@link PlatformTools} the in-process copilot uses (4D), so an external MCP client — Claude Desktop,
 * MCP Inspector, another agent — can list and invoke {@code getTransactionHistory} / {@code getRecentAlerts}
 * / {@code getCaseDetails} / {@code runGraphQuery} against the running platform.
 *
 * <p>{@link MethodToolCallbackProvider} reflects over {@code PlatformTools}, so only its {@code @Tool}
 * methods are exported; the agent-support helpers (4E) are plain methods and stay internal.
 */
@Configuration
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider platformToolCallbackProvider(PlatformTools platformTools) {
        return MethodToolCallbackProvider.builder().toolObjects(platformTools).build();
    }
}
