package com.dolos.casework.casecmd.aggregate;

import com.dolos.casework.casecmd.CaseAssigned;
import com.dolos.casework.casecmd.CaseClosed;
import com.dolos.casework.casecmd.CaseOpened;
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
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 3B — {@code Case} aggregate unit tests using Axon's {@link AggregateTestFixture}
 * (given/when/expect). Each {@code given(...)} replays prior events to rehydrate the aggregate before
 * the {@code when(...)} command runs — so these also exercise the event-sourcing handlers (the replay
 * path), not just command handling. No database, no Spring context.
 */
class CaseTest {

    private FixtureConfiguration<Case> fixture;

    private final UUID caseId = UUID.randomUUID();
    private final UUID alertId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Case.class);
    }

    @Test
    void opensACase() {
        fixture
                .givenNoPriorActivity()
                .when(new OpenCase(caseId, alertId, "ACC-1", 80, "system"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new CaseOpened(caseId, alertId, "ACC-1", 80, "system"));
    }

    @Test
    void rejectsANegativeScore() {
        fixture
                .givenNoPriorActivity()
                .when(new OpenCase(caseId, alertId, "ACC-1", -1, "system"))
                .expectException(IllegalArgumentException.class)
                .expectNoEvents();
    }

    @Test
    void assignsAnOpenCase() {
        fixture
                .given(new CaseOpened(caseId, alertId, "ACC-1", 80, "system"))
                .when(new AssignCase(caseId, "alice", "lead"))
                .expectEvents(new CaseAssigned(caseId, "alice", "lead"));
    }

    @Test
    void addsEvidence() {
        fixture
                .given(new CaseOpened(caseId, alertId, "ACC-1", 80, "system"))
                .when(new AddEvidence(caseId, "counterparty is a known mule", "alice"))
                .expectEvents(new EvidenceAdded(caseId, "counterparty is a known mule", "alice"));
    }

    @Test
    void drivesTheFullLifecycle() {
        fixture
                .given(
                        new CaseOpened(caseId, alertId, "ACC-1", 80, "system"),
                        new CaseAssigned(caseId, "alice", "lead"))
                .when(new Escalate(caseId, "structuring pattern", "senior"))
                .expectEvents(new Escalated(caseId, "structuring pattern", "senior"));

        fixture
                .given(
                        new CaseOpened(caseId, alertId, "ACC-1", 80, "system"),
                        new CaseAssigned(caseId, "alice", "lead"),
                        new Escalated(caseId, "structuring pattern", "senior"))
                .when(new FileReport(caseId, "SAR-2026-0001", "senior"))
                .expectEvents(new ReportFiled(caseId, "SAR-2026-0001", "senior"));

        fixture
                .given(
                        new CaseOpened(caseId, alertId, "ACC-1", 80, "system"),
                        new ReportFiled(caseId, "SAR-2026-0001", "senior"))
                .when(new CloseCase(caseId, "reported", "senior"))
                .expectEvents(new CaseClosed(caseId, "reported", "senior"));
    }

    @Test
    void rejectsAnyCommandOnAClosedCase() {
        fixture
                .given(
                        new CaseOpened(caseId, alertId, "ACC-1", 80, "system"),
                        new CaseClosed(caseId, "no action", "system"))
                .when(new AddEvidence(caseId, "too late", "alice"))
                .expectException(IllegalStateException.class)
                .expectNoEvents();
    }
}
