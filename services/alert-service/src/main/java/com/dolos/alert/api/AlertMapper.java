package com.dolos.alert.api;

import com.dolos.alert.api.dto.AlertResponse;
import com.dolos.alert.domain.AlertEntity;
import com.dolos.common.AccountId;

/** Pure mapping from the alert JPA entity to its API DTO. */
public final class AlertMapper {

    private AlertMapper() {}

    public static AlertResponse toResponse(AlertEntity e) {
        return new AlertResponse(
                e.getId(),
                e.getTransactionId(),
                AccountId.of(e.getAccountId()),
                e.getScore(),
                e.getReasons(),
                e.getDetail(),
                e.getRaisedAt());
    }
}
