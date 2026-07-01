package com.dolos.legacy.route;

import com.dolos.legacy.feed.LegacyFeedParser;
import com.dolos.legacy.feed.LegacyRecordException;
import com.dolos.legacy.feed.LegacyTransactionRecord;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

/**
 * The parse step of the route: decode the current fixed-width line into a {@link LegacyTransactionRecord}
 * and flag the exchange for the downstream content-based router. On success the body becomes the record
 * and {@link LegacyHeaders#VALID} is {@code true}; on a {@link LegacyRecordException} the body is left as
 * the original line and {@link LegacyHeaders#VALID} is {@code false} with a {@link LegacyHeaders#REJECT_REASON}
 * — so the router can dead-letter it without aborting the rest of the file.
 */
@Component
public class LegacyRecordParseProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {
        String line = exchange.getIn().getBody(String.class);
        try {
            LegacyTransactionRecord record = LegacyFeedParser.parse(line);
            exchange.getIn().setBody(record);
            exchange.getIn().setHeader(LegacyHeaders.VALID, true);
        } catch (LegacyRecordException e) {
            exchange.getIn().setBody(line);
            exchange.getIn().setHeader(LegacyHeaders.VALID, false);
            exchange.getIn().setHeader(LegacyHeaders.REJECT_REASON, e.getMessage());
        }
    }
}
