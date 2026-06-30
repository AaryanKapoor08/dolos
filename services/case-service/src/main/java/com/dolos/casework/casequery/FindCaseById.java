package com.dolos.casework.casequery;

import java.util.UUID;

/** Query: fetch a single case's current state + timeline. Answered with {@link CaseDetails}. */
public record FindCaseById(UUID caseId) {}
