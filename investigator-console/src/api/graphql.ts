import { config } from "../config";
import { token } from "../keycloak";

/**
 * Minimal GraphQL client against the BFF via the gateway (Phase 5D/5E). Every request carries the live
 * Keycloak bearer token, which the gateway validates and relays through the BFF to each backend. No
 * Apollo — a single typed fetch keeps the console dependency-light.
 */
export async function graphql<T>(
  query: string,
  variables: Record<string, unknown> = {},
): Promise<T> {
  const res = await fetch(`${config.gatewayUrl}/graphql`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token()}`,
    },
    body: JSON.stringify({ query, variables }),
  });

  if (!res.ok) {
    throw new Error(`GraphQL HTTP ${res.status}`);
  }

  const json = await res.json();
  if (json.errors?.length) {
    throw new Error(json.errors.map((e: { message: string }) => e.message).join("; "));
  }
  return json.data as T;
}
