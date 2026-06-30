package com.dolos.copilot.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the investigate-alert agent (Phase 4E). The SAR drafts the agent synthesizes are
 * written to a MinIO bucket separate from the RAG corpus; this binds its name from
 * {@code dolos.investigation.sar-bucket} (container override {@code DOLOS_INVESTIGATION_SAR_BUCKET}).
 *
 * @param sarBucket the MinIO bucket the agent writes SAR drafts to (created on startup if absent)
 */
@ConfigurationProperties(prefix = "dolos.investigation")
public record InvestigationProperties(String sarBucket) {}
