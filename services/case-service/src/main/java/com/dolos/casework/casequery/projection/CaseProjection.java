package com.dolos.casework.casequery.projection;

import com.dolos.casework.casecmd.CaseAssigned;
import com.dolos.casework.casecmd.CaseClosed;
import com.dolos.casework.casecmd.CaseOpened;
import com.dolos.casework.casecmd.CaseStatus;
import com.dolos.casework.casecmd.Escalated;
import com.dolos.casework.casecmd.EvidenceAdded;
import com.dolos.casework.casecmd.ReportFiled;
import com.dolos.casework.casequery.CaseDetails;
import com.dolos.casework.casequery.FindAllCases;
import com.dolos.casework.casequery.FindCaseById;
import com.dolos.casework.casequery.readmodel.CaseTimelineEntry;
import com.dolos.casework.casequery.readmodel.CaseView;
import com.dolos.casework.casequery.repo.CaseTimelineRepository;
import com.dolos.casework.casequery.repo.CaseViewRepository;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * The CQRS query side for cases (Phase 3C): a tracking event processor that projects the {@code Case}
 * domain events into the {@code case_view} + {@code case_timeline} read model, plus the
 * {@link QueryHandler}s that serve it. Because it is a tracking processor (its position lives in
 * {@code token_entry}), the read model survives restarts and can be rebuilt by replaying the event
 * store — each timeline row is keyed by the event id, so a replay re-projects idempotently.
 *
 * <p>After every projected event it pushes the fresh {@link CaseDetails} to any active
 * <b>subscription query</b> for that case (live updates — consumed by notifications in Phase 5).
 *
 * <p>Internal to {@code casequery}; depends on {@code casecmd} only for the event types it consumes.
 */
@Component
@ProcessingGroup("case-projection")
public class CaseProjection {

    private static final int SUMMARY_MAX = 512;

    private final CaseViewRepository views;
    private final CaseTimelineRepository timeline;
    private final QueryUpdateEmitter updateEmitter;

    public CaseProjection(
            CaseViewRepository views,
            CaseTimelineRepository timeline,
            QueryUpdateEmitter updateEmitter) {
        this.views = views;
        this.timeline = timeline;
        this.updateEmitter = updateEmitter;
    }

    // --- Projection: one handler per domain event -------------------------------------------------

    @EventHandler
    void on(CaseOpened event, DomainEventMessage<?> message) {
        views.save(
                new CaseView(
                        event.caseId(),
                        CaseStatus.OPEN,
                        event.alertId(),
                        event.accountId(),
                        event.score(),
                        event.openedBy(),
                        message.getTimestamp()));
        record(
                message,
                event.caseId(),
                "OPENED",
                "Case opened for account " + event.accountId() + " (score " + event.score() + ")",
                event.openedBy());
        emitUpdate(event.caseId());
    }

    @EventHandler
    void on(CaseAssigned event, DomainEventMessage<?> message) {
        update(
                event.caseId(),
                message,
                view -> {
                    view.setStatus(CaseStatus.ASSIGNED);
                    view.setAssignee(event.assignee());
                });
        record(message, event.caseId(), "ASSIGNED", "Assigned to " + event.assignee(), event.assignedBy());
        emitUpdate(event.caseId());
    }

    @EventHandler
    void on(EvidenceAdded event, DomainEventMessage<?> message) {
        update(event.caseId(), message, view -> {}); // no status change — just refresh updatedAt
        record(message, event.caseId(), "EVIDENCE", event.note(), event.addedBy());
        emitUpdate(event.caseId());
    }

    @EventHandler
    void on(Escalated event, DomainEventMessage<?> message) {
        update(event.caseId(), message, view -> view.setStatus(CaseStatus.ESCALATED));
        record(message, event.caseId(), "ESCALATED", "Escalated: " + event.reason(), event.escalatedBy());
        emitUpdate(event.caseId());
    }

    @EventHandler
    void on(ReportFiled event, DomainEventMessage<?> message) {
        update(
                event.caseId(),
                message,
                view -> {
                    view.setStatus(CaseStatus.REPORT_FILED);
                    view.setReportReference(event.reportReference());
                });
        record(
                message,
                event.caseId(),
                "REPORT_FILED",
                "Report filed: " + event.reportReference(),
                event.filedBy());
        emitUpdate(event.caseId());
    }

    @EventHandler
    void on(CaseClosed event, DomainEventMessage<?> message) {
        update(
                event.caseId(),
                message,
                view -> {
                    view.setStatus(CaseStatus.CLOSED);
                    view.setResolution(event.resolution());
                });
        record(message, event.caseId(), "CLOSED", "Closed: " + event.resolution(), event.closedBy());
        emitUpdate(event.caseId());
    }

    // --- Query handlers ---------------------------------------------------------------------------

    @QueryHandler
    public CaseDetails handle(FindCaseById query) {
        return views.findById(query.caseId()).map(this::toDetails).orElse(null);
    }

    @QueryHandler
    public List<CaseDetails> handle(FindAllCases query) {
        return views.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .map(this::toDetails)
                .toList();
    }

    // --- Helpers ----------------------------------------------------------------------------------

    private void update(UUID caseId, DomainEventMessage<?> message, Consumer<CaseView> mutation) {
        views.findById(caseId)
                .ifPresent(
                        view -> {
                            mutation.accept(view);
                            view.setUpdatedAt(message.getTimestamp());
                            views.save(view);
                        });
    }

    private void record(
            DomainEventMessage<?> message, UUID caseId, String type, String summary, String actor) {
        timeline.save(
                new CaseTimelineEntry(
                        message.getIdentifier(),
                        caseId,
                        message.getSequenceNumber(),
                        type,
                        truncate(summary),
                        actor,
                        message.getTimestamp()));
    }

    private void emitUpdate(UUID caseId) {
        CaseDetails details = handle(new FindCaseById(caseId));
        if (details != null) {
            updateEmitter.emit(FindCaseById.class, query -> query.caseId().equals(caseId), details);
        }
    }

    private CaseDetails toDetails(CaseView view) {
        List<CaseDetails.TimelineItem> items =
                timeline.findByCaseIdOrderBySequenceAsc(view.getCaseId()).stream()
                        .map(
                                t ->
                                        new CaseDetails.TimelineItem(
                                                t.getSequence(),
                                                t.getType(),
                                                t.getSummary(),
                                                t.getActor(),
                                                t.getOccurredAt()))
                        .toList();
        return new CaseDetails(
                view.getCaseId(),
                view.getStatus().name(),
                view.getAlertId(),
                view.getAccountId(),
                view.getScore(),
                view.getAssignee(),
                view.getOpenedBy(),
                view.getOpenedAt(),
                view.getUpdatedAt(),
                view.getReportReference(),
                view.getResolution(),
                items);
    }

    private static String truncate(String s) {
        return s.length() <= SUMMARY_MAX ? s : s.substring(0, SUMMARY_MAX);
    }
}
