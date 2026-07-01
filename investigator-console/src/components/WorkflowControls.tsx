import { useState } from "react";
import { caseCommands } from "../api/caseCommands";
import { hasRole } from "../keycloak";
import type { Case } from "../types";

/**
 * The BPMN case-workflow controls (Phase 5E). Each button dispatches a case-service command through the
 * gateway (assign / add evidence / escalate / file report / close). Escalate and File report are
 * senior-only — disabled unless the logged-in user holds SENIOR_ANALYST (case-service also enforces this
 * server-side via @PreAuthorize, so this is UX, not the security boundary). All controls are disabled
 * until a case exists for the alert.
 */
export function WorkflowControls({
  kase,
  onAction,
}: {
  kase: Case | null;
  onAction: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const senior = hasRole("SENIOR_ANALYST");

  async function run(label: string, fn: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await fn();
      onAction();
    } catch (e) {
      setError(`${label} failed: ${String(e)}`);
    } finally {
      setBusy(false);
    }
  }

  if (!kase) {
    return <div className="muted">Open a case for this alert to drive the workflow.</div>;
  }

  const id = kase.caseId;
  const ask = (q: string, fallback = "") => window.prompt(q, fallback)?.trim();

  return (
    <div className="workflow">
      <div className="wf-row">
        <button
          className="btn"
          disabled={busy}
          onClick={() => {
            const assignee = ask("Assign to (username):", "analyst");
            if (assignee) run("Assign", () => caseCommands.assign(id, assignee));
          }}
        >
          Assign
        </button>
        <button
          className="btn"
          disabled={busy}
          onClick={() => {
            const note = ask("Evidence note:");
            if (note) run("Add evidence", () => caseCommands.addEvidence(id, note));
          }}
        >
          Add evidence
        </button>
      </div>

      <div className="wf-row">
        <button
          className="btn warn"
          disabled={busy || !senior}
          title={senior ? "" : "Senior analysts only"}
          onClick={() => {
            const reason = ask("Escalation reason:");
            if (reason) run("Escalate", () => caseCommands.escalate(id, reason));
          }}
        >
          Escalate
        </button>
        <button
          className="btn warn"
          disabled={busy || !senior}
          title={senior ? "" : "Senior analysts only"}
          onClick={() => {
            const ref = ask("SAR/STR report reference:", "SAR-2026-0001");
            if (ref) run("File report", () => caseCommands.fileReport(id, ref));
          }}
        >
          File report
        </button>
      </div>

      <div className="wf-row">
        <button
          className="btn danger"
          disabled={busy}
          onClick={() => {
            const resolution = ask("Closing resolution:", "CONFIRMED_FRAUD");
            if (resolution) run("Close", () => caseCommands.close(id, resolution));
          }}
        >
          Close case
        </button>
      </div>

      {error && <div className="banner error small">{error}</div>}
    </div>
  );
}
