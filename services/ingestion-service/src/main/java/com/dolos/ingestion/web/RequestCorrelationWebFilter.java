package com.dolos.ingestion.web;

import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Reactive counterpart of transaction-service's correlation filter. Derives a {@code traceId}
 * (from the inbound {@code X-Trace-Id} header or freshly generated) and a per-request
 * {@code requestId}, echoes the trace id back on the response, and stashes both in the Reactor
 * Context so downstream operators can read them.
 *
 * <p>Note: bridging these ids into the SLF4J MDC for every reactive log line requires
 * context propagation that lands with the OpenTelemetry baseline in Phase 6A; until then this
 * filter guarantees caller-facing trace correlation via the response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationWebFilter implements WebFilter {

    static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String TRACE_ID_KEY = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String inbound = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        String traceId = (inbound == null || inbound.isBlank()) ? UUID.randomUUID().toString() : inbound;
        String requestId = UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(TRACE_ID_KEY, traceId).put(REQUEST_ID_KEY, requestId));
    }
}
