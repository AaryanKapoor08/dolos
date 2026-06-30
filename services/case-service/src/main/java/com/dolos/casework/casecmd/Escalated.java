package com.dolos.casework.casecmd;

import java.util.UUID;

/**
 * The case was escalated (e.g. to a senior analyst / for SAR consideration). A senior-only action
 * once method security lands in Phase 3F.
 *
 * @param caseId       the case aggregate id
 * @param reason       why it was escalated
 * @param escalatedBy  who escalated it
 */
public record Escalated(UUID caseId, String reason, String escalatedBy) {}
