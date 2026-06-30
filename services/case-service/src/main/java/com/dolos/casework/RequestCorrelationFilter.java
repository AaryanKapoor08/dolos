package com.dolos.casework;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates per-request correlation identifiers into the SLF4J MDC so every log line emitted while
 * handling a request carries them. The JSON log encoder includes MDC entries automatically. Lives in
 * the application root package so it is shared by all modules (it is not a module of its own).
 *
 * <ul>
 *   <li>{@code requestId} — always generated fresh per request.</li>
 *   <li>{@code traceId} — taken from the inbound {@code X-Trace-Id} header when present (so a
 *       caller-supplied trace id propagates into our logs), otherwise generated. Echoed back on the
 *       response so callers can correlate.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    static final String TRACE_ID_HEADER = "X-Trace-Id";
    static final String REQUEST_ID_KEY = "requestId";
    static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(REQUEST_ID_KEY, UUID.randomUUID().toString());
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
