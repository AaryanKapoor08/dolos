import { useCallback, useEffect, useState } from "react";
import { fetchAlertDetail } from "../api/queries";
import type { AlertDetail } from "../types";
import { Timeline } from "./Timeline";
import { WorkflowControls } from "./WorkflowControls";
import { CopilotChat } from "./CopilotChat";
import { FraudGraph } from "./FraudGraph";

/**
 * The case workspace (Phase 5E). One BFF query ({@code fetchAlertDetail}) fills the whole screen: the
 * alert, its event-sourced case + timeline, and its account's fraud-graph neighbourhood — demonstrating
 * the 5D aggregation. From here the analyst drives the BPMN workflow and asks the copilot.
 */
export function CaseView({
  alertId,
  onCaseChanged,
}: {
  alertId: string;
  onCaseChanged: () => void;
}) {
  const [detail, setDetail] = useState<AlertDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setDetail(await fetchAlertDetail(alertId));
      setError(null);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }, [alertId]);

  useEffect(() => {
    load();
  }, [load]);

  const afterAction = useCallback(() => {
    load();
    onCaseChanged();
  }, [load, onCaseChanged]);

  if (loading && !detail) return <div className="empty">Loading case…</div>;
  if (error) return <div className="banner error">Failed to load: {error}</div>;
  if (!detail) return <div className="empty">Alert {alertId} not found.</div>;

  const kase = detail.case ?? null;

  return (
    <div className="caseview">
      <div className="case-header">
        <div>
          <h1>{detail.title ?? detail.alertId}</h1>
          <div className="sub">
            <span className={`chip sev-${(detail.severity ?? "LOW").toLowerCase()}`}>
              {detail.severity}
            </span>
            <span className="muted">
              account {detail.accountId} · score {detail.score} ·{" "}
              {detail.alertType ?? "TRANSACTION"}
            </span>
          </div>
          {detail.reasons?.length > 0 && (
            <div className="reasons">
              {detail.reasons.map((r) => (
                <span key={r} className="chip reason">
                  {r}
                </span>
              ))}
            </div>
          )}
          {detail.detail && <p className="detail-text">{detail.detail}</p>}
        </div>
        <div className="case-meta">
          {kase ? (
            <>
              <div className="kv">
                <span>Case</span>
                <code>{kase.caseId.slice(0, 8)}</code>
              </div>
              <div className="kv">
                <span>Status</span>
                <b>{kase.status}</b>
              </div>
              <div className="kv">
                <span>Assignee</span>
                <b>{kase.assignee ?? "—"}</b>
              </div>
            </>
          ) : (
            <div className="muted">No case opened for this alert yet.</div>
          )}
        </div>
      </div>

      <div className="grid">
        <div className="card graph-card">
          <h3>Account graph {detail.accountGraph?.inRing && <span className="chip ring">IN RING</span>}</h3>
          <FraudGraph graph={detail.accountGraph ?? null} centerId={detail.accountId ?? ""} />
        </div>

        <div className="card">
          <h3>Workflow</h3>
          <WorkflowControls kase={kase} onAction={afterAction} />
        </div>

        <div className="card">
          <h3>Timeline</h3>
          <Timeline items={kase?.timeline ?? []} />
        </div>

        <div className="card copilot-card">
          <h3>AI copilot</h3>
          <CopilotChat alertId={detail.alertId} accountId={detail.accountId ?? ""} />
        </div>
      </div>
    </div>
  );
}
