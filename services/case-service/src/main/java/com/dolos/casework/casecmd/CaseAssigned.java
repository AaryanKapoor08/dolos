package com.dolos.casework.casecmd;

import java.util.UUID;

/**
 * The case was assigned (or reassigned) to an analyst.
 *
 * @param caseId     the case aggregate id
 * @param assignee   the analyst the case is now assigned to
 * @param assignedBy who performed the assignment
 */
public record CaseAssigned(UUID caseId, String assignee, String assignedBy) {}
