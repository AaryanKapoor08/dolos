import { graphql } from "./graphql";
import type { AccountGraph, Alert, AlertDetail, Case } from "../types";

/**
 * The GraphQL documents the console uses. The DoD query for Phase 5D lives in {@link fetchAlertDetail}:
 * a single round trip returns the alert together with its case, its triggering transaction, and its
 * account-graph neighbourhood.
 */

const ALERT_QUEUE = /* GraphQL */ `
  query AlertQueue($size: Int!) {
    alertQueue(size: $size) {
      alertId
      severity
      title
      accountId
      score
      raisedAt
    }
  }
`;

const ALERT_DETAIL = /* GraphQL */ `
  query AlertDetail($id: ID!) {
    alert(id: $id) {
      alertId
      alertType
      severity
      title
      transactionId
      accountId
      score
      reasons
      detail
      raisedAt
      case {
        caseId
        status
        assignee
        openedBy
        openedAt
        updatedAt
        resolution
        reportReference
        timeline { sequence type summary actor occurredAt }
      }
      accountGraph {
        accountId
        inRing
        rings
        owners
        devices
        outgoing { counterparty amount occurredAt }
        incoming { counterparty amount occurredAt }
      }
    }
  }
`;

const CASE_BY_ID = /* GraphQL */ `
  query CaseById($id: ID!) {
    case(id: $id) {
      caseId
      status
      assignee
      openedBy
      openedAt
      updatedAt
      resolution
      reportReference
      timeline { sequence type summary actor occurredAt }
    }
  }
`;

const ACCOUNT_GRAPH = /* GraphQL */ `
  query AccountGraph($id: ID!) {
    accountGraph(id: $id) {
      accountId
      inRing
      rings
      owners
      devices
      outgoing { counterparty amount occurredAt }
      incoming { counterparty amount occurredAt }
    }
  }
`;

const COPILOT = /* GraphQL */ `
  mutation Copilot($question: String!) {
    copilot(input: { question: $question }) { reply }
  }
`;

export async function fetchAlertQueue(size = 25): Promise<Alert[]> {
  const data = await graphql<{ alertQueue: Alert[] }>(ALERT_QUEUE, { size });
  return data.alertQueue ?? [];
}

export async function fetchAlertDetail(id: string): Promise<AlertDetail | null> {
  const data = await graphql<{ alert: AlertDetail | null }>(ALERT_DETAIL, { id });
  return data.alert;
}

export async function fetchCase(id: string): Promise<Case | null> {
  const data = await graphql<{ case: Case | null }>(CASE_BY_ID, { id });
  return data.case;
}

export async function fetchAccountGraph(id: string): Promise<AccountGraph | null> {
  const data = await graphql<{ accountGraph: AccountGraph | null }>(ACCOUNT_GRAPH, { id });
  return data.accountGraph;
}

export async function askCopilot(question: string): Promise<string> {
  const data = await graphql<{ copilot: { reply: string } }>(COPILOT, { question });
  return data.copilot.reply;
}
