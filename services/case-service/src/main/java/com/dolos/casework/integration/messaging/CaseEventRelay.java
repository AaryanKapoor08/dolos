package com.dolos.casework.integration.messaging;

import com.dolos.casework.casecmd.CaseClosed;
import com.dolos.casework.casecmd.CaseOpened;
import com.dolos.casework.casecmd.Escalated;
import com.dolos.casework.casecmd.ReportFiled;
import com.dolos.events.CaseEscalated;
import com.dolos.events.CaseReportFiled;
import java.time.Instant;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.Timestamp;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Bridges the service-internal Axon domain events to the published {@code dolos-events} integration
 * contract (Phase 3E). It translates each lifecycle event and re-publishes it as a Spring application
 * event via {@link ApplicationEventPublisher}.
 *
 * <p>This handler's processing group ({@code case-integration}) is configured as a <b>subscribing</b>
 * processor, so it runs synchronously inside the command's transaction. That is what makes the outbox
 * transactional: Spring Modulith persists the resulting event-publication row in the <em>same</em>
 * transaction as the Axon event (the state change). The actual Kafka send happens after commit in
 * {@link CaseEventPublisher}.
 *
 * <p>Subscribing processors don't replay history, so only case events from now on are externalized —
 * which is what we want (no backfill of pre-3E cases onto Kafka).
 */
@Component
@ProcessingGroup("case-integration")
public class CaseEventRelay {

    private final ApplicationEventPublisher publisher;

    public CaseEventRelay(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @EventHandler
    void on(CaseOpened event, @Timestamp Instant occurredAt) {
        publisher.publishEvent(
                new com.dolos.events.CaseOpened(
                        event.caseId(),
                        event.alertId(),
                        event.accountId(),
                        event.score(),
                        event.openedBy(),
                        occurredAt));
    }

    @EventHandler
    void on(Escalated event, @Timestamp Instant occurredAt) {
        publisher.publishEvent(
                new CaseEscalated(event.caseId(), event.reason(), event.escalatedBy(), occurredAt));
    }

    @EventHandler
    void on(ReportFiled event, @Timestamp Instant occurredAt) {
        publisher.publishEvent(
                new CaseReportFiled(
                        event.caseId(), event.reportReference(), event.filedBy(), occurredAt));
    }

    @EventHandler
    void on(CaseClosed event, @Timestamp Instant occurredAt) {
        publisher.publishEvent(
                new com.dolos.events.CaseClosed(
                        event.caseId(), event.resolution(), event.closedBy(), occurredAt));
    }
}
