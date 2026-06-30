package com.dolos.casework.casecmd.command;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Command: assign (or reassign) the case to an analyst. */
public record AssignCase(
        @TargetAggregateIdentifier UUID caseId, String assignee, String assignedBy) {}
