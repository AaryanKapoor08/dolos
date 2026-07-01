package com.dolos.legacy.route;

/**
 * Exchange-header names the route uses to carry per-record routing decisions between the parse step and
 * the content-based router. Kept in one place so the {@code RouteBuilder} and the processors cannot
 * drift on a string literal.
 */
public final class LegacyHeaders {

    /** Boolean: did the record parse into a valid {@code LegacyTransactionRecord}? Drives the router. */
    public static final String VALID = "DolosRecordValid";

    /** Human-readable reason a record was rejected (only set when {@link #VALID} is false). */
    public static final String REJECT_REASON = "DolosRejectReason";

    /** 0-based index of the record within the file (for log context). */
    public static final String LINE_NUMBER = "DolosLineNumber";

    /** The subject account id — set by the translator so the post-publish log line can name it. */
    public static final String ACCOUNT_ID = "DolosAccountId";

    private LegacyHeaders() {}
}
