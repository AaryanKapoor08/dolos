package com.dolos.legacy.feed;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Decodes one line of the legacy partner's fixed-width feed into a validated
 * {@link LegacyTransactionRecord}, throwing {@link LegacyRecordException} for anything malformed. The
 * layout is column-positional (the partner's mainframe never learned JSON):
 *
 * <pre>
 *   cols  1- 2  record type    ("TX")
 *   cols  3-18  partner ref     (16, left-justified)
 *   cols 19-30  account id      (12, left-justified)
 *   cols 31-42  counterparty    (12, blank = none)
 *   cols 43-55  amount          (13, MINOR units, right-justified zero-padded)
 *   cols 56-58  currency        ( 3, ISO-4217)
 *   col     59  direction       ( 1, 'D' debit / 'C' credit)
 *   cols 60-61  country         ( 2, ISO-3166 alpha-2, blank = none)
 *   cols 62-75  occurred at     (14, yyyyMMddHHmmss, UTC)
 *   cols 76-105 description     (30, blank = none)
 * </pre>
 *
 * A record must be long enough to carry every mandatory column (through {@code occurredAt}); the
 * trailing description is optional.
 */
public final class LegacyFeedParser {

    /** Minimum line length: everything through the mandatory {@code occurredAt} column (col 75). */
    private static final int MIN_LENGTH = 75;

    private static final DateTimeFormatter OCCURRED_AT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private LegacyFeedParser() {}

    public static LegacyTransactionRecord parse(String line) {
        if (line == null || line.length() < MIN_LENGTH) {
            throw new LegacyRecordException(
                    "line too short (" + (line == null ? 0 : line.length()) + " < " + MIN_LENGTH + ")");
        }

        String recordType = field(line, 0, 2);
        if (!"TX".equals(recordType)) {
            throw new LegacyRecordException("unexpected record type '" + recordType + "' (want 'TX')");
        }

        String partnerRef = field(line, 2, 18);
        if (partnerRef.isEmpty()) {
            throw new LegacyRecordException("missing partner reference");
        }

        String accountId = field(line, 18, 30);
        if (accountId.isEmpty()) {
            throw new LegacyRecordException("missing account id");
        }

        String counterparty = blankToNull(field(line, 30, 42));

        BigDecimal amount = parseAmount(field(line, 42, 55));

        String currency = field(line, 55, 58);
        if (currency.length() != 3) {
            throw new LegacyRecordException("currency '" + currency + "' is not a 3-letter ISO-4217 code");
        }

        String direction = parseDirection(field(line, 58, 59));
        String country = blankToNull(field(line, 59, 61));
        var occurredAt = parseOccurredAt(field(line, 61, 75));
        String description = line.length() > 75 ? blankToNull(field(line, 75, Math.min(line.length(), 105))) : null;

        return new LegacyTransactionRecord(
                recordType, partnerRef, accountId, counterparty, amount, currency, direction, country,
                occurredAt.toInstant(ZoneOffset.UTC), description);
    }

    /** Trimmed substring, tolerant of a line shorter than {@code end}. */
    private static String field(String line, int start, int end) {
        return line.substring(start, Math.min(end, line.length())).trim();
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static BigDecimal parseAmount(String minorUnits) {
        long minor;
        try {
            minor = Long.parseLong(minorUnits);
        } catch (NumberFormatException e) {
            throw new LegacyRecordException("amount '" + minorUnits + "' is not numeric");
        }
        if (minor <= 0) {
            throw new LegacyRecordException("amount must be positive, was " + minor);
        }
        // The feed carries minor units (cents); the canonical event uses major units.
        return BigDecimal.valueOf(minor).movePointLeft(2);
    }

    private static String parseDirection(String flag) {
        return switch (flag) {
            case "D" -> "DEBIT";
            case "C" -> "CREDIT";
            default -> throw new LegacyRecordException("direction '" + flag + "' is not 'D' or 'C'");
        };
    }

    private static LocalDateTime parseOccurredAt(String raw) {
        try {
            return LocalDateTime.parse(raw, OCCURRED_AT);
        } catch (DateTimeParseException e) {
            throw new LegacyRecordException("occurredAt '" + raw + "' is not yyyyMMddHHmmss");
        }
    }
}
