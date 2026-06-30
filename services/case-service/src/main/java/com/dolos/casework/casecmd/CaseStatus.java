package com.dolos.casework.casecmd;

/**
 * The lifecycle state of an investigation case. Part of {@code casecmd}'s exposed API: the query side
 * stores it on the read model, and the workflow drives transitions through it.
 *
 * <pre>
 *   OPEN ──assign──▶ ASSIGNED ──escalate──▶ ESCALATED
 *     │                  │                      │
 *     └───────── file report ───────────▶ REPORT_FILED ──close──▶ CLOSED (terminal)
 * </pre>
 *
 * <p>Transitions are enforced by the {@code Case} aggregate, not here; CLOSED is terminal.
 */
public enum CaseStatus {
    OPEN,
    ASSIGNED,
    ESCALATED,
    REPORT_FILED,
    CLOSED
}
