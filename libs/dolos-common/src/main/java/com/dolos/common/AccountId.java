package com.dolos.common;

import java.util.Objects;

/** Stable identifier for an account. Framework-agnostic value object reused across services. */
public record AccountId(String value) {

    public AccountId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("AccountId must not be blank");
        }
    }

    public static AccountId of(String value) {
        return new AccountId(value);
    }
}
