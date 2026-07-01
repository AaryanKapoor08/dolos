import { useCallback, useEffect, useMemo, useState } from "react";
import ForceGraph2D from "react-force-graph-2d";
import { fetchAccountGraph } from "../api/queries";
import type { AccountGraph } from "../types";

interface GNode {
  id: string; // unique key used to resolve links (prefixed for owners/devices)
  name: string; // display value
  kind: "account" | "owner" | "device";
  ring: boolean;
  center: boolean;
}
interface GLink {
  source: string;
  target: string;
  label: string;
}

/**
 * The interactive fraud-ring graph (Phase 5F). Renders an account's one-hop neighbourhood from the BFF
 * `accountGraph` as a force-directed web: the subject account at the centre, its owners/devices, and its
 * money-flow counterparties (directional arrows). Ring members are highlighted red. Clicking an account
 * node expands it — fetching that account's neighbourhood and merging it in — so an analyst can walk an
 * A→B→C→D ring outward from the alert.
 */
export function FraudGraph({
  graph,
  centerId,
}: {
  graph: AccountGraph | null;
  centerId: string;
}) {
  // Accumulate neighbourhoods as the analyst expands nodes; reset when the base graph changes.
  const [hoods, setHoods] = useState<AccountGraph[]>([]);
  const [expanding, setExpanding] = useState<string | null>(null);

  useEffect(() => {
    setHoods(graph ? [graph] : []);
  }, [graph]);

  const expand = useCallback(
    async (accountId: string) => {
      if (hoods.some((h) => h.accountId === accountId)) return;
      setExpanding(accountId);
      try {
        const more = await fetchAccountGraph(accountId);
        if (more) setHoods((prev) => [...prev, more]);
      } finally {
        setExpanding(null);
      }
    },
    [hoods],
  );

  const data = useMemo(() => buildGraph(hoods, centerId), [hoods, centerId]);

  if (!graph) return <div className="muted">No graph data for this account.</div>;

  return (
    <div className="graph-wrap">
      <ForceGraph2D
        graphData={data}
        width={480}
        height={320}
        cooldownTicks={80}
        nodeRelSize={5}
        nodeLabel={(n) => `${(n as GNode).kind}: ${(n as GNode).name}`}
        nodeColor={(n) => colorOf(n as GNode)}
        linkLabel={(l) => (l as unknown as GLink).label}
        linkDirectionalArrowLength={4}
        linkDirectionalArrowRelPos={1}
        linkColor={() => "#8895a7"}
        onNodeClick={(n) => {
          const node = n as GNode;
          if (node.kind === "account") expand(node.name);
        }}
      />
      <div className="graph-legend">
        <span className="lg center">subject</span>
        <span className="lg ring">ring member</span>
        <span className="lg account">account</span>
        <span className="lg owner">owner</span>
        <span className="lg device">device</span>
        {expanding && <span className="muted">expanding {expanding.slice(0, 8)}…</span>}
        <span className="muted">click an account to expand</span>
      </div>
    </div>
  );
}

function colorOf(n: GNode): string {
  if (n.center) return "#2563eb";
  if (n.ring) return "#dc2626";
  if (n.kind === "owner") return "#16a34a";
  if (n.kind === "device") return "#a855f7";
  return "#64748b";
}

/** Fold every fetched neighbourhood into one node/link set, de-duping by id. */
function buildGraph(hoods: AccountGraph[], centerId: string) {
  const nodes = new Map<string, GNode>();
  const links: GLink[] = [];
  const ringAccounts = new Set<string>();
  hoods.forEach((h) => {
    if (h.inRing) ringAccounts.add(h.accountId);
  });

  const account = (id: string) => {
    if (!nodes.has(id)) {
      nodes.set(id, {
        id,
        name: id,
        kind: "account",
        ring: ringAccounts.has(id),
        center: id === centerId,
      });
    } else if (ringAccounts.has(id)) {
      nodes.get(id)!.ring = true;
    }
  };

  hoods.forEach((h) => {
    account(h.accountId);
    h.owners.forEach((o) => {
      const key = `owner:${o}`;
      nodes.set(key, { id: key, name: o, kind: "owner", ring: false, center: false });
      links.push({ source: key, target: h.accountId, label: "OWNS" });
    });
    h.devices.forEach((d) => {
      const key = `device:${d}`;
      nodes.set(key, { id: key, name: d, kind: "device", ring: false, center: false });
      links.push({ source: h.accountId, target: key, label: "USED" });
    });
    h.outgoing.forEach((e) => {
      account(e.counterparty);
      links.push({ source: h.accountId, target: e.counterparty, label: `→ ${e.amount ?? ""}` });
    });
    h.incoming.forEach((e) => {
      account(e.counterparty);
      links.push({ source: e.counterparty, target: h.accountId, label: `→ ${e.amount ?? ""}` });
    });
  });

  return { nodes: Array.from(nodes.values()), links };
}
