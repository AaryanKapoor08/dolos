package com.dolos.copilot.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the copilot's platform tools (Phase 4D) reach the rest of Dolos: the base URLs of the
 * transaction / alert / graph / case services, plus the Keycloak client-credentials grant used to
 * obtain a service token for the (secured) case-service. Bound from {@code dolos.platform.*}; the
 * container overrides each value via {@code DOLOS_PLATFORM_*} (see services.yml).
 *
 * @param transactionBaseUrl     transaction-service origin (canonical store) — getTransactionHistory
 * @param alertBaseUrl           alert-service origin — getRecentAlerts
 * @param graphBaseUrl           graph-service origin — runGraphQuery (neighborhood + ring membership)
 * @param caseBaseUrl            case-service origin (secured) — getCaseDetails
 * @param transactionHistoryLimit how many recent transactions a history tool call returns
 * @param keycloak               client-credentials config for the case-service service token
 */
@ConfigurationProperties(prefix = "dolos.platform")
public record PlatformProperties(
        String transactionBaseUrl,
        String alertBaseUrl,
        String graphBaseUrl,
        String caseBaseUrl,
        int transactionHistoryLimit,
        Keycloak keycloak) {

    /** Client-credentials grant the copilot uses to call the secured case-service (Phase 3F). */
    public record Keycloak(String tokenUri, String clientId, String clientSecret) {}
}
