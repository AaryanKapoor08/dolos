package com.dolos.casework.casecmd.command;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command: open a new case. Internal to {@code casecmd} — callers go through
 * {@link com.dolos.casework.casecmd.CaseCommandService}, never the raw command. The {@code caseId} is
 * the aggregate identifier (the creation command carries it).
 */
public record OpenCase(
        @TargetAggregateIdentifier UUID caseId,
        UUID alertId,
        String accountId,
        int score,
        String openedBy) {}
