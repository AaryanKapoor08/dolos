package com.dolos.casework.casecmd.command;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Command: attach a piece of evidence (a note/finding) to the case. */
public record AddEvidence(
        @TargetAggregateIdentifier UUID caseId, String note, String addedBy) {}
