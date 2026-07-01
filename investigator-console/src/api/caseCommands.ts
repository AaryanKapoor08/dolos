import { config } from "../config";
import { token } from "../keycloak";

/**
 * Case workflow commands (Phase 5E) — the write side. These POST to case-service through the gateway
 * (/api/cases/**); the bearer token identifies the acting analyst (recorded as the event actor) and
 * gates the senior-only actions (escalate / file report) via case-service's @PreAuthorize.
 */
async function post(path: string, body: unknown): Promise<void> {
  const res = await fetch(`${config.gatewayUrl}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token()}`,
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`${res.status} ${res.statusText} ${text}`.trim());
  }
}

export const caseCommands = {
  assign: (id: string, assignee: string) => post(`/api/cases/${id}/assign`, { assignee }),
  addEvidence: (id: string, note: string) => post(`/api/cases/${id}/evidence`, { note }),
  escalate: (id: string, reason: string) => post(`/api/cases/${id}/escalate`, { reason }),
  fileReport: (id: string, reportReference: string) =>
    post(`/api/cases/${id}/report`, { reportReference }),
  close: (id: string, resolution: string) => post(`/api/cases/${id}/close`, { resolution }),
};
