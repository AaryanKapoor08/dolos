package com.dolos.alert.api;

import com.dolos.alert.api.dto.AlertResponse;
import com.dolos.alert.domain.AlertView;
import com.dolos.common.AccountId;

/** Pure mapping from the CQRS read model ({@link AlertView}) to its API DTO (Phase 2F). */
public final class AlertMapper {

    private AlertMapper() {}

    public static AlertResponse toResponse(AlertView v) {
        return new AlertResponse(
                v.getAlertId(),
                v.getAlertType(),
                v.getSeverity().name(),
                v.getTitle(),
                v.getTransactionId(),
                AccountId.of(v.getAccountId()),
                v.getScore(),
                v.getReasons(),
                v.getDetail(),
                v.getRaisedAt());
    }
}
