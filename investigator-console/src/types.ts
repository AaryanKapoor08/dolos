// Shapes mirroring the GraphQL BFF schema (services/bff-service/.../schema.graphqls) and the STOMP
// notification frame. Kept intentionally partial — only the fields the console renders.

export interface Alert {
  alertId: string;
  alertType?: string;
  severity?: string;
  title?: string;
  transactionId?: string | null;
  accountId?: string | null;
  score: number;
  reasons: string[];
  detail?: string;
  raisedAt?: string;
}

export interface TimelineItem {
  sequence: number;
  type?: string;
  summary?: string;
  actor?: string;
  occurredAt?: string;
}

export interface Case {
  caseId: string;
  status?: string;
  alertId?: string | null;
  accountId?: string | null;
  score: number;
  assignee?: string | null;
  openedBy?: string;
  openedAt?: string;
  updatedAt?: string;
  reportReference?: string | null;
  resolution?: string | null;
  timeline: TimelineItem[];
}

export interface GraphEdge {
  counterparty: string;
  amount?: string;
  occurredAt?: string;
}

export interface AccountGraph {
  accountId: string;
  inRing: boolean;
  rings: string[];
  owners: string[];
  devices: string[];
  outgoing: GraphEdge[];
  incoming: GraphEdge[];
}

/** The composed one-query payload behind a selected alert (alert + case + transaction + graph). */
export interface AlertDetail extends Alert {
  case?: Case | null;
  accountGraph?: AccountGraph | null;
}

/** A live frame pushed over STOMP (notification-service). */
export interface Notification {
  kind: "ALERT_RAISED" | "CASE_OPENED" | "CASE_ESCALATED" | "CASE_CLOSED";
  entityId: string;
  accountId?: string | null;
  title: string;
  at: string;
}
