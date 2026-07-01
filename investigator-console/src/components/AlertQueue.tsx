import type { Alert } from "../types";

/**
 * The risk-sorted alert queue (Phase 5E), served by the BFF's `alertQueue`. Clicking a row selects it
 * and opens its case in the workspace. Severity drives the colour chip.
 */
export function AlertQueue({
  alerts,
  selectedId,
  onSelect,
}: {
  alerts: Alert[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  return (
    <div className="panel queue">
      <h2>Alert queue</h2>
      {alerts.length === 0 && <div className="muted">No alerts yet.</div>}
      <ul>
        {alerts.map((a) => (
          <li
            key={a.alertId}
            className={a.alertId === selectedId ? "row selected" : "row"}
            onClick={() => onSelect(a.alertId)}
          >
            <span className={`chip sev-${(a.severity ?? "LOW").toLowerCase()}`}>
              {a.severity ?? "—"}
            </span>
            <div className="row-main">
              <div className="row-title">{a.title ?? a.alertId}</div>
              <div className="row-sub">
                {a.accountId ?? "—"} · score {a.score}
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
