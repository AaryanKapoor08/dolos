// Central place for the environment-driven endpoints. Vite inlines VITE_* at build time; the defaults
// keep the app working against a standard local stack even without a .env file.
export const config = {
  gatewayUrl: import.meta.env.VITE_GATEWAY_URL ?? "http://localhost:8080",
  keycloak: {
    url: import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8087",
    realm: import.meta.env.VITE_KEYCLOAK_REALM ?? "dolos",
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? "dolos-console",
  },
};
