package com.dolos.legacy.feed;

/**
 * Thrown by {@link LegacyFeedParser} when a fixed-width line cannot be decoded into a valid
 * {@link LegacyTransactionRecord} (too short, wrong record type, unparseable amount/date, etc.). The
 * route catches it to dead-letter the offending record rather than fail the whole file — the
 * content-based-router half of this EIP demo.
 */
public class LegacyRecordException extends RuntimeException {

    @java.io.Serial private static final long serialVersionUID = 1L;

    public LegacyRecordException(String message) {
        super(message);
    }
}
