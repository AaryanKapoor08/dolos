package com.dolos.casework.casecmd;

import java.util.UUID;

/**
 * The case was closed. Terminal — no further commands are accepted on a closed case.
 *
 * @param caseId     the case aggregate id
 * @param resolution the closing disposition (e.g. "reported", "no action")
 * @param closedBy   who closed it
 */
public record CaseClosed(UUID caseId, String resolution, String closedBy) {}
