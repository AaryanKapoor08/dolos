package com.dolos.casework.casecmd;

import java.util.UUID;

/**
 * A regulatory report (e.g. a SAR) was filed for the case.
 *
 * @param caseId          the case aggregate id
 * @param reportReference the external reference / id of the filed report
 * @param filedBy         who filed it
 */
public record ReportFiled(UUID caseId, String reportReference, String filedBy) {}
