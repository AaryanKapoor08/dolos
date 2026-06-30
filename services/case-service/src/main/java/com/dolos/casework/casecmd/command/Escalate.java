package com.dolos.casework.casecmd.command;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Command: escalate the case (senior-only once Phase 3F method security lands). */
public record Escalate(
        @TargetAggregateIdentifier UUID caseId, String reason, String escalatedBy) {}
