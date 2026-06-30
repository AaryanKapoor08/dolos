package com.dolos.casework.casecmd.aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import com.dolos.casework.casecmd.CaseAssigned;
import com.dolos.casework.casecmd.CaseClosed;
import com.dolos.casework.casecmd.CaseOpened;
import com.dolos.casework.casecmd.CaseStatus;
import com.dolos.casework.casecmd.Escalated;
import com.dolos.casework.casecmd.EvidenceAdded;
import com.dolos.casework.casecmd.ReportFiled;
import com.dolos.casework.casecmd.command.AddEvidence;
import com.dolos.casework.casecmd.command.AssignCase;
import com.dolos.casework.casecmd.command.CloseCase;
import com.dolos.casework.casecmd.command.Escalate;
import com.dolos.casework.casecmd.command.FileReport;
import com.dolos.casework.casecmd.command.OpenCase;
import java.util.UUID;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

/**
 * The event-sourced {@code Case} aggregate (Axon command side, Phase 3B).
 *
 * <p>Its state is never stored directly: every command validates against the current in-memory state
 * and, if valid, {@code apply(...)}s a domain event. Axon persists those events to the JPA event store
 * and rehydrates the aggregate by replaying them through the {@link EventSourcingHandler}s. The only
 * state we keep here is what the command invariants need ({@code caseId} and {@code status}); richer
 * projections live on the read model (Phase 3C).
 *
 * <p>Internal to {@code casecmd} — driven via
 * {@link com.dolos.casework.casecmd.CaseCommandService}.
 */
@Aggregate
public class Case {

    @AggregateIdentifier private UUID caseId;
    private CaseStatus status;

    /** Required by Axon for event-sourced rehydration. */
    protected Case() {}

    /** Creation command: opens the case and emits the first event in its stream. */
    @CommandHandler
    public Case(OpenCase command) {
        if (command.score() < 0) {
            throw new IllegalArgumentException("score must be >= 0");
        }
        if (command.accountId() == null || command.accountId().isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        apply(
                new CaseOpened(
                        command.caseId(),
                        command.alertId(),
                        command.accountId(),
                        command.score(),
                        command.openedBy()));
    }

    @CommandHandler
    public void handle(AssignCase command) {
        assertNotClosed();
        apply(new CaseAssigned(caseId, command.assignee(), command.assignedBy()));
    }

    @CommandHandler
    public void handle(AddEvidence command) {
        assertNotClosed();
        apply(new EvidenceAdded(caseId, command.note(), command.addedBy()));
    }

    @CommandHandler
    public void handle(Escalate command) {
        assertNotClosed();
        apply(new Escalated(caseId, command.reason(), command.escalatedBy()));
    }

    @CommandHandler
    public void handle(FileReport command) {
        assertNotClosed();
        apply(new ReportFiled(caseId, command.reportReference(), command.filedBy()));
    }

    @CommandHandler
    public void handle(CloseCase command) {
        assertNotClosed();
        apply(new CaseClosed(caseId, command.resolution(), command.closedBy()));
    }

    // --- Event-sourcing handlers: rebuild state by replaying the stream ----------------------------

    @EventSourcingHandler
    void on(CaseOpened event) {
        this.caseId = event.caseId();
        this.status = CaseStatus.OPEN;
    }

    @EventSourcingHandler
    void on(CaseAssigned event) {
        this.status = CaseStatus.ASSIGNED;
    }

    @EventSourcingHandler
    void on(Escalated event) {
        this.status = CaseStatus.ESCALATED;
    }

    @EventSourcingHandler
    void on(ReportFiled event) {
        this.status = CaseStatus.REPORT_FILED;
    }

    @EventSourcingHandler
    void on(CaseClosed event) {
        this.status = CaseStatus.CLOSED;
    }

    // EvidenceAdded carries no state transition, so it needs no event-sourcing handler.

    private void assertNotClosed() {
        if (status == CaseStatus.CLOSED) {
            throw new IllegalStateException("case " + caseId + " is closed");
        }
    }
}
