package com.dolos.copilot.tools;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activates {@link PlatformProperties} so the platform tools (Phase 4D) and the service-token provider
 * pick up their {@code dolos.platform.*} configuration. The {@link RestClient}s themselves are built in
 * {@link PlatformTools}/{@link ServiceTokenProvider} from the auto-configured {@code RestClient.Builder}.
 */
@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformToolsConfig {}
