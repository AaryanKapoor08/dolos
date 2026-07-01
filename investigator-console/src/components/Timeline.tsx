import type { TimelineItem } from "../types";

/**
 * The case's event-sourced timeline (Phase 5E) — every command that touched the aggregate, in order,
 * straight from case-service's CQRS read model via the BFF.
 */
export function Timeline({ items }: { items: TimelineItem[] }) {
  if (items.length === 0) return <div className="muted">No events yet.</div>;
  return (
    <ol className="timeline">
      {items.map((it) => (
        <li key={it.sequence}>
          <span className="tl-type">{it.type}</span>
          <span className="tl-summary">{it.summary}</span>
          <span className="tl-actor muted">
            {it.actor}
            {it.occurredAt ? ` · ${new Date(it.occurredAt).toLocaleString()}` : ""}
          </span>
        </li>
      ))}
    </ol>
  );
}
