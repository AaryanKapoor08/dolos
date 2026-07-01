package com.dolos.legacy.route;

import com.dolos.events.Topics;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * The one code-defined Camel route for the legacy partner feed (Phase 6D) — a compact tour of three
 * classic Enterprise Integration Patterns:
 *
 * <ul>
 *   <li><b>File poller</b>: {@code from("file:...")} watches a bind-mounted inbox; a processed file is
 *       moved to {@code .done} (or {@code .error} on an infrastructure failure) so it is never
 *       reprocessed.</li>
 *   <li><b>Splitter</b>: each file fans out into one exchange per line, streamed so a large file never
 *       loads whole into memory.</li>
 *   <li><b>Content-based router</b>: the parse step flags each record valid/invalid, and {@code choice()}
 *       sends valid records through the <b>message translator</b> to the canonical
 *       {@code transactions.received} topic while <b>dead-lettering</b> malformed ones to a
 *       {@code .rejected} file — so one bad row never sinks the batch.</li>
 * </ul>
 *
 * <p>The Kafka broker list and this route's inbox are externalized (see application.yml); the producer
 * inherits the observation instrumentation from camel-observation, so its records carry the W3C
 * {@code traceparent} that joins the 6A distributed trace.
 */
@Component
public class LegacyFeedRouteBuilder extends RouteBuilder {

    private final LegacyRecordParseProcessor parseProcessor;
    private final CanonicalTransactionTranslator translator;

    public LegacyFeedRouteBuilder(
            LegacyRecordParseProcessor parseProcessor, CanonicalTransactionTranslator translator) {
        this.parseProcessor = parseProcessor;
        this.translator = translator;
    }

    @Override
    public void configure() {
        // Poll the inbox; readLock=changed waits for the file to stop growing before picking it up, so a
        // partner still writing the file isn't read mid-flush. Processed -> .done, infra failure -> .error.
        from("file:{{dolos.legacy.inbound-dir}}"
                        + "?move=.done&moveFailed=.error&readLock=changed&readLockTimeout=15000"
                        + "&include=.*\\.(txt|dat)&sortBy=file:name")
                .routeId("legacy-partner-feed")
                .log("Legacy partner feed received: ${header.CamelFileName}")
                .split(body().tokenize("\n"))
                .streaming()
                .setHeader(LegacyHeaders.LINE_NUMBER, simple("${exchangeProperty.CamelSplitIndex}"))
                // Skip blank / whitespace-only lines (trailing newline, separator rows).
                .filter(simple("${body.trim().length()} > 0"))
                .process(parseProcessor)
                .choice()
                .when(header(LegacyHeaders.VALID).isEqualTo(true))
                .process(translator)
                .log("Translated legacy record ${header." + LegacyHeaders.LINE_NUMBER
                        + "} -> TransactionReceived for account ${header." + LegacyHeaders.ACCOUNT_ID + "}")
                // Keep the wire header-less (per Topics): drop our internal routing headers before the
                // producer maps exchange headers onto the Kafka record. The observation-injected
                // `traceparent` is added at send time (not an exchange header), so it survives.
                .removeHeaders("Dolos*")
                .to("kafka:" + Topics.TRANSACTIONS_RECEIVED)
                .otherwise()
                .log(
                        LoggingLevel.WARN,
                        "Rejected malformed legacy record (line ${header." + LegacyHeaders.LINE_NUMBER
                                + "}): ${header." + LegacyHeaders.REJECT_REASON + "}")
                .setBody(
                        simple(
                                "REJECTED line ${header." + LegacyHeaders.LINE_NUMBER
                                        + "} (${header." + LegacyHeaders.REJECT_REASON + "}): ${body}\n"))
                .to("file:{{dolos.legacy.inbound-dir}}/.rejected?fileName=rejected.txt&fileExist=Append")
                .end() // choice
                .end() // filter
                .end(); // split
    }
}
