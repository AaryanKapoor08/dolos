/**
 * Dolos shared security starter (Phase 3F): an auto-configured OAuth2/OIDC resource server. Applying
 * the {@code dolos-security} dependency and setting {@code spring.security.oauth2.resourceserver.jwt.*}
 * secures every endpoint with a Keycloak-issued JWT and maps realm roles to {@code ROLE_*} authorities;
 * {@code @PreAuthorize} method security is enabled for role-gated actions. Used by case-service now, by
 * every service behind the gateway in Phase 5.
 */
package com.dolos.security;
