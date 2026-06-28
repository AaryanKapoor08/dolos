package com.dolos.alert.service;

import com.dolos.alert.api.AlertMapper;
import com.dolos.alert.api.dto.AlertResponse;
import com.dolos.alert.domain.AlertEntity;
import com.dolos.alert.grpc.ScoreDetailClient;
import com.dolos.alert.grpc.ScoreDetailView;
import com.dolos.alert.repo.AlertRepository;
import com.dolos.events.AlertRaised;
import com.dolos.events.RiskScored;
import com.dolos.events.Topics;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for raising and querying alerts (Phase 1E).
 *
 * <p>Write path: a {@link RiskScored} at or above the configured threshold becomes an {@link
 * AlertEntity} and an {@link AlertRaised} event. Idempotent on the transaction id — the unique
 * constraint on {@code transaction_id} is the real guard, with an {@code existsByTransactionId}
 * fast-path and a duplicate-key catch handling the redelivery race — so a redelivered score never
 * double-alerts. The {@code AlertRaised} event is published only after the row is committed.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository repository;
    private final KafkaTemplate<String, Object> kafka;
    private final ScoreDetailClient scoreDetailClient;
    private final int scoreThreshold;

    public AlertService(
            AlertRepository repository,
            KafkaTemplate<String, Object> kafka,
            ScoreDetailClient scoreDetailClient,
            @Value("${dolos.alert.score-threshold}") int scoreThreshold) {
        this.repository = repository;
        this.kafka = kafka;
        this.scoreDetailClient = scoreDetailClient;
        this.scoreThreshold = scoreThreshold;
    }

    /**
     * Handles a scored transaction: persists an alert and publishes {@code AlertRaised} when the
     * score crosses the threshold and no alert yet exists for the transaction.
     */
    public void handle(RiskScored scored) {
        if (scored.score() < scoreThreshold) {
            log.debug(
                    "Score {} for txn {} below threshold {} — no alert",
                    scored.score(),
                    scored.transactionId(),
                    scoreThreshold);
            return;
        }

        AlertEntity persisted = persistIfNew(scored);
        if (persisted == null) {
            log.debug(
                    "Alert already exists for txn {} — idempotent skip", scored.transactionId());
            return;
        }

        AlertRaised event =
                new AlertRaised(
                        persisted.getId(),
                        persisted.getTransactionId(),
                        persisted.getAccountId(),
                        persisted.getScore(),
                        persisted.getReasons(),
                        persisted.getRaisedAt());
        kafka.send(Topics.ALERTS_RAISED, event.accountId(), event);
        log.info(
                "Raised alert {} for txn {} (account {}, score {})",
                persisted.getId(),
                persisted.getTransactionId(),
                persisted.getAccountId(),
                persisted.getScore());
    }

    /**
     * Persists a new alert for the scored transaction, or returns {@code null} if one already
     * exists. Not annotated {@code @Transactional} on purpose: it is called via {@code this} from
     * {@link #handle}, where a self-invoked transactional method would be bypassed by the proxy
     * anyway. Spring Data's {@code save} is itself transactional and commits before it returns, so
     * by the time {@code handle} publishes {@code AlertRaised} the row is already durable.
     */
    AlertEntity persistIfNew(RiskScored scored) {
        if (repository.existsByTransactionId(scored.transactionId())) {
            return null;
        }
        // Synchronously enrich with the full score detail over gRPC; Resilience4j guarantees this
        // returns (a real detail, or the "details unavailable" fallback) even if scoring is down.
        ScoreDetailView detail = scoreDetailClient.getScoreDetails(scored.transactionId());
        AlertEntity entity =
                new AlertEntity(
                        UUID.randomUUID(),
                        scored.transactionId(),
                        scored.accountId(),
                        scored.score(),
                        scored.reasons(),
                        detail.summary(),
                        Instant.now());
        try {
            return repository.save(entity);
        } catch (DataIntegrityViolationException duplicate) {
            // A redelivery racing the existsByTransactionId check already inserted this alert.
            // The unique constraint on transaction_id held the line — treat it as the no-op it is.
            return null;
        }
    }

    /** Paged, risk-sorted view of raised alerts for the analyst queue. */
    @Transactional(readOnly = true)
    public Page<AlertResponse> findAlerts(Pageable pageable) {
        return repository.findAll(pageable).map(AlertMapper::toResponse);
    }
}
