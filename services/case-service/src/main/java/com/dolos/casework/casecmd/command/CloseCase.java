package com.dolos.casework.casecmd.command;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Command: close the case (terminal). */
public record CloseCase(
        @TargetAggregateIdentifier UUID caseId, String resolution, String closedBy) {}
