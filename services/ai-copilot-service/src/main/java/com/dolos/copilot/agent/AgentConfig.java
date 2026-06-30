package com.dolos.copilot.agent;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activates {@link InvestigationProperties} so the investigate-alert agent (Phase 4E) picks up its
 * {@code dolos.investigation.*} configuration (the SAR bucket). The {@code ChatClient} and
 * {@code MinioClient} the agent uses are auto-configured / already provided by the RAG wiring.
 */
@Configuration
@EnableConfigurationProperties(InvestigationProperties.class)
public class AgentConfig {}
