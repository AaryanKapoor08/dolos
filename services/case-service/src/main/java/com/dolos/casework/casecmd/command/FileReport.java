package com.dolos.casework.casecmd.command;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Command: file a regulatory report (e.g. a SAR) for the case. */
public record FileReport(
        @TargetAggregateIdentifier UUID caseId, String reportReference, String filedBy) {}
