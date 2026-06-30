package com.dolos.casework.integration.intake;

import com.dolos.casework.casecmd.CaseCommandService;
import com.dolos.events.AlertRaised;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a HIGH {@code AlertRaised} into an investigation case (Phase 3E). Only alerts at/above the
 * configured score open a case; lower-severity alerts are ignored (they stay on the alert queue).
 *
 * <p>Idempotent: the {@code OpenCase} command and the {@link AlertCaseLink} dedupe row are written in
 * one transaction, so a redelivery of the same alert finds the link and skips rather than opening a
 * second case. Opening from the alert is the automatic path; an analyst can still open a case by hand
 * via the command API.
 */
@Service
public class CaseIntakeService {

    private static final Logger log = LoggerFactory.getLogger(CaseIntakeService.class);
    private static final String OPENED_BY = "system";

    private final CaseCommandService commands;
    private final AlertCaseLinkRepository links;
    private final int highScore;

    public CaseIntakeService(
            CaseCommandService commands,
            AlertCaseLinkRepository links,
            @Value("${dolos.case.high-score-threshold}") int highScore) {
        this.commands = commands;
        this.links = links;
        this.highScore = highScore;
    }

    @Transactional
    public void onAlert(AlertRaised alert) {
        if (alert.score() < highScore) {
            return; // not severe enough to warrant an investigation case
        }
        if (links.existsById(alert.alertId())) {
            return; // a case was already opened for this alert (redelivery)
        }
        UUID caseId =
                commands.openCase(alert.alertId(), alert.accountId(), alert.score(), OPENED_BY);
        links.save(new AlertCaseLink(alert.alertId(), caseId, Instant.now()));
        log.info(
                "opened case {} for HIGH alert {} (account {}, score {})",
                caseId,
                alert.alertId(),
                alert.accountId(),
                alert.score());
    }
}
