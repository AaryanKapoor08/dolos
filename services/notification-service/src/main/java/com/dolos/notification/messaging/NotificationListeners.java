package com.dolos.notification.messaging;

import com.dolos.events.AlertRaised;
import com.dolos.events.CaseClosed;
import com.dolos.events.CaseEscalated;
import com.dolos.events.CaseOpened;
import com.dolos.events.Topics;
import com.dolos.notification.api.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * The bridge from the event backbone to the browser (Phase 5C). Each listener reads a raw JSON event
 * off its topic, parses it to the corresponding {@code dolos-events} record, and pushes a UI-shaped
 * {@link Notification} to a STOMP destination: alerts to {@code /topic/alerts}, all case lifecycle
 * events to {@code /topic/cases}. Idempotent by construction — re-broadcasting a redelivered event just
 * re-pushes an identical frame, which the console de-dupes on {@code entityId}. Runs on virtual threads
 * (see {@code KafkaConsumerConfig}).
 */
@Component
public class NotificationListeners {

    private static final Logger log = LoggerFactory.getLogger(NotificationListeners.class);

    static final String TOPIC_ALERTS = "/topic/alerts";
    static final String TOPIC_CASES = "/topic/cases";

    private final SimpMessagingTemplate messaging;
    private final ObjectMapper mapper;

    public NotificationListeners(SimpMessagingTemplate messaging, ObjectMapper eventObjectMapper) {
        this.messaging = messaging;
        this.mapper = eventObjectMapper;
    }

    @KafkaListener(topics = Topics.ALERTS_RAISED, groupId = "${spring.kafka.consumer.group-id}")
    public void onAlertRaised(String payload) throws Exception {
        AlertRaised e = mapper.readValue(payload, AlertRaised.class);
        push(
                TOPIC_ALERTS,
                new Notification(
                        "ALERT_RAISED",
                        e.alertId().toString(),
                        e.accountId(),
                        "Alert raised · score " + e.score() + " · " + e.accountId(),
                        e.raisedAt() != null ? e.raisedAt() : Instant.now()));
    }

    @KafkaListener(topics = Topics.CASES_OPENED, groupId = "${spring.kafka.consumer.group-id}")
    public void onCaseOpened(String payload) throws Exception {
        CaseOpened e = mapper.readValue(payload, CaseOpened.class);
        push(
                TOPIC_CASES,
                new Notification(
                        "CASE_OPENED",
                        e.caseId().toString(),
                        e.accountId(),
                        "Case opened · " + e.accountId() + " · by " + e.openedBy(),
                        e.openedAt() != null ? e.openedAt() : Instant.now()));
    }

    @KafkaListener(topics = Topics.CASES_ESCALATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onCaseEscalated(String payload) throws Exception {
        CaseEscalated e = mapper.readValue(payload, CaseEscalated.class);
        push(
                TOPIC_CASES,
                new Notification(
                        "CASE_ESCALATED",
                        e.caseId().toString(),
                        null,
                        "Case escalated · " + e.reason(),
                        e.escalatedAt() != null ? e.escalatedAt() : Instant.now()));
    }

    @KafkaListener(topics = Topics.CASES_CLOSED, groupId = "${spring.kafka.consumer.group-id}")
    public void onCaseClosed(String payload) throws Exception {
        CaseClosed e = mapper.readValue(payload, CaseClosed.class);
        push(
                TOPIC_CASES,
                new Notification(
                        "CASE_CLOSED",
                        e.caseId().toString(),
                        null,
                        "Case closed · " + e.resolution(),
                        e.closedAt() != null ? e.closedAt() : Instant.now()));
    }

    private void push(String destination, Notification notification) {
        log.info("push kind={} entityId={} -> {}", notification.kind(), notification.entityId(), destination);
        messaging.convertAndSend(destination, notification);
    }
}
