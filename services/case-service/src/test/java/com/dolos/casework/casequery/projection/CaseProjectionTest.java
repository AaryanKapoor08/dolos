package com.dolos.casework.casequery.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dolos.casework.casecmd.CaseAssigned;
import com.dolos.casework.casecmd.CaseClosed;
import com.dolos.casework.casecmd.CaseOpened;
import com.dolos.casework.casecmd.CaseStatus;
import com.dolos.casework.casecmd.Escalated;
import com.dolos.casework.casecmd.EvidenceAdded;
import com.dolos.casework.casecmd.ReportFiled;
import com.dolos.casework.casequery.readmodel.CaseTimelineEntry;
import com.dolos.casework.casequery.readmodel.CaseView;
import com.dolos.casework.casequery.repo.CaseTimelineRepository;
import com.dolos.casework.casequery.repo.CaseViewRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 3C — projection unit test. Feeds the full event history of a case into {@link CaseProjection}
 * and asserts the resulting read model reflects it (current state + ordered timeline). The
 * repositories are backed by in-memory maps via Mockito answers, so it runs locally with no database.
 */
class CaseProjectionTest {

    private final Map<UUID, CaseView> viewStore = new HashMap<>();
    private final List<CaseTimelineEntry> timelineStore = new ArrayList<>();

    private CaseProjection projection;
    private long sequence;

    private final UUID caseId = UUID.randomUUID();
    private final UUID alertId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CaseViewRepository views = mock(CaseViewRepository.class);
        CaseTimelineRepository timeline = mock(CaseTimelineRepository.class);
        QueryUpdateEmitter emitter = mock(QueryUpdateEmitter.class);

        when(views.save(any(CaseView.class)))
                .thenAnswer(
                        inv -> {
                            CaseView v = inv.getArgument(0);
                            viewStore.put(v.getCaseId(), v);
                            return v;
                        });
        when(views.findById(any()))
                .thenAnswer(inv -> Optional.ofNullable(viewStore.get(inv.getArgument(0))));
        when(timeline.save(any(CaseTimelineEntry.class)))
                .thenAnswer(
                        inv -> {
                            CaseTimelineEntry t = inv.getArgument(0);
                            timelineStore.add(t);
                            return t;
                        });
        when(timeline.findByCaseIdOrderBySequenceAsc(any()))
                .thenAnswer(
                        inv ->
                                timelineStore.stream()
                                        .filter(t -> t.getCaseId().equals(inv.getArgument(0)))
                                        .sorted(Comparator.comparingLong(CaseTimelineEntry::getSequence))
                                        .toList());

        projection = new CaseProjection(views, timeline, emitter);
        sequence = 0;
    }

    @Test
    void projectsTheFullEventHistory() {
        projection.on(new CaseOpened(caseId, alertId, "ACC-1", 80, "system"), message());
        projection.on(new CaseAssigned(caseId, "alice", "lead"), message());
        projection.on(new EvidenceAdded(caseId, "known mule counterparty", "alice"), message());
        projection.on(new Escalated(caseId, "structuring", "senior"), message());
        projection.on(new ReportFiled(caseId, "SAR-2026-0001", "senior"), message());
        projection.on(new CaseClosed(caseId, "reported", "senior"), message());

        CaseView view = viewStore.get(caseId);
        assertThat(view.getStatus()).isEqualTo(CaseStatus.CLOSED);
        assertThat(view.getAssignee()).isEqualTo("alice");
        assertThat(view.getReportReference()).isEqualTo("SAR-2026-0001");
        assertThat(view.getResolution()).isEqualTo("reported");
        assertThat(view.getUpdatedAt()).isAfterOrEqualTo(view.getOpenedAt());

        assertThat(timelineStore).hasSize(6);
        assertThat(timelineStore.stream().map(CaseTimelineEntry::getType).toList())
                .containsExactly("OPENED", "ASSIGNED", "EVIDENCE", "ESCALATED", "REPORT_FILED", "CLOSED");
        assertThat(timelineStore.stream().map(CaseTimelineEntry::getSequence).toList())
                .containsExactly(0L, 1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void leavesNoCaseForAnUnknownId() {
        assertThat(projection.handle(new com.dolos.casework.casequery.FindCaseById(caseId))).isNull();
    }

    /** A domain event message at the next stream position; payload is irrelevant to the projector. */
    private GenericDomainEventMessage<Object> message() {
        return new GenericDomainEventMessage<>("Case", caseId.toString(), sequence++, "payload");
    }
}
