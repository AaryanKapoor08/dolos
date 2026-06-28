package com.dolos.alert.service;

import com.dolos.alert.domain.AlertEntity;
import com.dolos.alert.domain.AlertView;
import com.dolos.alert.repo.AlertViewRepository;
import org.springframework.stereotype.Component;

/**
 * Projects the normalized {@link AlertEntity} write model into the denormalized {@link AlertView}
 * read model (Phase 2F): the query side of CQRS within alert-service. Precomputes the severity bucket
 * and a one-line title so the analyst queue renders with no joins or post-processing.
 *
 * <p>The upsert is keyed on the alert id, so projecting the same alert twice is idempotent — combined
 * with the write side's unique {@code dedupe_key} (which prevents a duplicate alert ever being
 * created), replaying the same event can never produce a duplicate queue row.
 */
@Component
public class AlertProjector {

    private static final int MAX_TITLE = 256;

    private final AlertViewRepository readModel;

    public AlertProjector(AlertViewRepository readModel) {
        this.readModel = readModel;
    }

    public void project(AlertEntity alert) {
        AlertView view =
                new AlertView(
                        alert.getId(),
                        alert.getDedupeKey(),
                        alert.getAlertType().name(),
                        alert.getTransactionId(),
                        alert.getAccountId(),
                        alert.getScore(),
                        severityOf(alert.getScore()),
                        titleOf(alert),
                        alert.getReasons(),
                        alert.getDetail(),
                        alert.getRaisedAt());
        // save() merges by the assigned id: INSERT first time, idempotent overwrite on replay.
        readModel.save(view);
    }

    static AlertView.Severity severityOf(int score) {
        if (score >= 80) {
            return AlertView.Severity.HIGH;
        }
        if (score >= 60) {
            return AlertView.Severity.MEDIUM;
        }
        return AlertView.Severity.LOW;
    }

    private static String titleOf(AlertEntity alert) {
        String headline;
        if (alert.getAlertType() == AlertEntity.Type.RING) {
            headline = alert.getDetail() != null ? alert.getDetail() : "Mule ring detected";
        } else if (alert.getReasons() != null && !alert.getReasons().isEmpty()) {
            headline = alert.getReasons().get(0);
        } else {
            headline = "Risk score " + alert.getScore();
        }
        return headline.length() > MAX_TITLE ? headline.substring(0, MAX_TITLE) : headline;
    }
}
