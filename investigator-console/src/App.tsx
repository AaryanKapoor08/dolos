import { useCallback, useEffect, useState } from "react";
import { keycloak, username, hasRole } from "./keycloak";
import { fetchAlertQueue } from "./api/queries";
import { connectNotifications } from "./api/notifications";
import type { Alert, Notification } from "./types";
import { AlertQueue } from "./components/AlertQueue";
import { CaseView } from "./components/CaseView";
import { NotificationsFeed } from "./components/NotificationsFeed";

/**
 * The Investigator Console shell (Phase 5E). Left: the alert queue + the live notification feed.
 * Right: the selected alert's case workspace (detail, graph, timeline, workflow, copilot). A new
 * AlertRaised frame over STOMP refreshes the queue so the analyst sees it arrive without reloading.
 */
export default function App() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [selectedAlertId, setSelectedAlertId] = useState<string | null>(null);
  const [feed, setFeed] = useState<Notification[]>([]);
  const [error, setError] = useState<string | null>(null);

  const refreshQueue = useCallback(async () => {
    try {
      const queue = await fetchAlertQueue(25);
      setAlerts(queue);
      setError(null);
      setSelectedAlertId((current) => current ?? queue[0]?.alertId ?? null);
    } catch (e) {
      setError(`Failed to load the alert queue: ${String(e)}`);
    }
  }, []);

  useEffect(() => {
    refreshQueue();
  }, [refreshQueue]);

  // Live feed: prepend every notification; a new alert also nudges the queue to refetch.
  useEffect(() => {
    const disconnect = connectNotifications((n) => {
      setFeed((prev) => [n, ...prev].slice(0, 50));
      if (n.kind === "ALERT_RAISED") {
        refreshQueue();
      }
    });
    return disconnect;
  }, [refreshQueue]);

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="logo">◆</span> Dolos · Investigator Console
        </div>
        <div className="user">
          <span className="who">
            {username()}
            {hasRole("SENIOR_ANALYST") ? " · senior" : ""}
          </span>
          <button className="btn ghost" onClick={() => keycloak.logout()}>
            Sign out
          </button>
        </div>
      </header>

      {error && <div className="banner error">{error}</div>}

      <main className="layout">
        <aside className="sidebar">
          <AlertQueue
            alerts={alerts}
            selectedId={selectedAlertId}
            onSelect={setSelectedAlertId}
          />
          <NotificationsFeed feed={feed} />
        </aside>

        <section className="workspace">
          {selectedAlertId ? (
            <CaseView alertId={selectedAlertId} onCaseChanged={refreshQueue} />
          ) : (
            <div className="empty">Select an alert to open its case.</div>
          )}
        </section>
      </main>
    </div>
  );
}
