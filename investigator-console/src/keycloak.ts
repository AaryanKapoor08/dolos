import Keycloak from "keycloak-js";
import { config } from "./config";

/**
 * The single Keycloak client for the console (Phase 5E). Points at the `dolos` realm's public
 * `dolos-console` client and uses Authorization Code + PKCE (S256) — the browser-safe flow (no client
 * secret). One instance is shared across the app so every API call reads the same live token.
 */
export const keycloak = new Keycloak({
  url: config.keycloak.url,
  realm: config.keycloak.realm,
  clientId: config.keycloak.clientId,
});

/**
 * Boots the login flow. `login-required` redirects an anonymous visitor straight to Keycloak, so the
 * app only ever renders for an authenticated analyst. A refresh timer keeps the access token fresh
 * while the tab is open. Resolves once we hold a valid token.
 */
export async function initAuth(): Promise<void> {
  await keycloak.init({
    onLoad: "login-required",
    pkceMethod: "S256",
    checkLoginIframe: false,
  });

  // Refresh the token ~30s before expiry so long investigations don't get bounced.
  setInterval(() => {
    keycloak.updateToken(60).catch(() => keycloak.login());
  }, 30_000);
}

/** The current bearer token (refreshed by the timer above). */
export function token(): string {
  return keycloak.token ?? "";
}

/** Realm roles for the logged-in user — drives which workflow controls are enabled. */
export function roles(): string[] {
  return keycloak.realmAccess?.roles ?? [];
}

export function hasRole(role: string): boolean {
  return roles().includes(role);
}

export function username(): string {
  return (keycloak.tokenParsed?.["preferred_username"] as string) ?? "unknown";
}
