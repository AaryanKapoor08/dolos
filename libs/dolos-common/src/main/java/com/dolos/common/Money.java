package com.dolos.common;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * A monetary amount in a specific currency. Framework-agnostic value object reused across services.
 *
 * <p>Kept deliberately simple for now (no cross-currency arithmetic); grows as the domain needs it.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
    }

    /** Convenience factory from an ISO-4217 currency code (e.g. "USD", "CAD"). */
    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }
}
