package com.dolos.legacy.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the fixed-width decoder — the logic behind the route's content-based router. A valid
 * line becomes a fully-normalized {@link LegacyTransactionRecord}; every malformed shape raises
 * {@link LegacyRecordException} so the route can dead-letter it instead of failing the whole file.
 */
class LegacyFeedParserTest {

    // A well-formed record: $15,000.00 DEBIT from ACC-1000001 to a new payee (cols verified in 6D).
    private static final String VALID =
            "TXLGCY000000000001ACC-1000001 NEWPAYEE00010000001500000USDDUS20260615103000Wire to overseas vendor";

    @Test
    void decodesAValidFixedWidthRecord() {
        LegacyTransactionRecord record = LegacyFeedParser.parse(VALID);

        assertThat(record.recordType()).isEqualTo("TX");
        assertThat(record.partnerRef()).isEqualTo("LGCY000000000001");
        assertThat(record.accountId()).isEqualTo("ACC-1000001");
        assertThat(record.counterpartyAccountId()).isEqualTo("NEWPAYEE0001");
        // Minor units (1,500,000) decode to major units.
        assertThat(record.amount()).isEqualByComparingTo(new BigDecimal("15000.00"));
        assertThat(record.currency()).isEqualTo("USD");
        assertThat(record.direction()).isEqualTo("DEBIT");
        assertThat(record.country()).isEqualTo("US");
        assertThat(record.occurredAt()).isEqualTo(Instant.parse("2026-06-15T10:30:00Z"));
        assertThat(record.description()).isEqualTo("Wire to overseas vendor");
    }

    @Test
    void mapsCreditFlagAndTreatsBlankOptionalColumnsAsNull() {
        // Same layout, direction 'C', blank counterparty + country, no description.
        String credit =
                "TXLGCY000000000009ACC-2000002             0000000900000GBPC  20260615090000";
        LegacyTransactionRecord record = LegacyFeedParser.parse(credit);

        assertThat(record.direction()).isEqualTo("CREDIT");
        assertThat(record.counterpartyAccountId()).isNull();
        assertThat(record.country()).isNull();
        assertThat(record.description()).isNull();
        assertThat(record.amount()).isEqualByComparingTo(new BigDecimal("9000.00"));
    }

    @Test
    void rejectsTooShortLine() {
        assertThatThrownBy(() -> LegacyFeedParser.parse("XX  this is a corrupt partner row"))
                .isInstanceOf(LegacyRecordException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void rejectsWrongRecordType() {
        String wrongType = "ZZ" + VALID.substring(2);
        assertThatThrownBy(() -> LegacyFeedParser.parse(wrongType))
                .isInstanceOf(LegacyRecordException.class)
                .hasMessageContaining("record type");
    }

    @Test
    void rejectsNonNumericAmount() {
        // Overwrite the amount column [42,55) with letters.
        String badAmount = VALID.substring(0, 42) + "NOTANUMBER123" + VALID.substring(55);
        assertThatThrownBy(() -> LegacyFeedParser.parse(badAmount))
                .isInstanceOf(LegacyRecordException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void rejectsUnknownDirectionFlag() {
        // Overwrite the direction column [58,59) with 'X'.
        String badDir = VALID.substring(0, 58) + "X" + VALID.substring(59);
        assertThatThrownBy(() -> LegacyFeedParser.parse(badDir))
                .isInstanceOf(LegacyRecordException.class)
                .hasMessageContaining("direction");
    }
}
