package com.dolos.casework.casecmd;

import java.util.UUID;

/**
 * A piece of evidence (a note, a finding, a linked transaction reference) was attached to the case.
 * Appends to the case timeline; does not change the case status.
 *
 * @param caseId  the case aggregate id
 * @param note    the evidence text
 * @param addedBy the analyst who added it
 */
public record EvidenceAdded(UUID caseId, String note, String addedBy) {}
