import type { Notification } from "../types";

/**
 * The live notification feed (Phase 5C/5E): STOMP frames from notification-service, newest first. This
 * is the visible proof of the real-time path — a new alert or case event appears here the instant it
 * flows through Kafka, no reload.
 */
export function NotificationsFeed({ feed }: { feed: Notification[] }) {
  return (
    <div className="panel feed">
      <h2>
        Live feed <span className="dot" title="connected" />
      </h2>
      {feed.length === 0 && <div className="muted">Waiting for events…</div>}
      <ul>
        {feed.map((n, i) => (
          <li key={`${n.entityId}-${i}`} className={`feed-item ${n.kind.toLowerCase()}`}>
            <span className="feed-kind">{n.kind.replace("_", " ")}</span>
            <span className="feed-title">{n.title}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
