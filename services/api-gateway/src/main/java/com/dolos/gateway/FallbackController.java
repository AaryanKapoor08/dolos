package com.dolos.gateway;

import com.dolos.common.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * The circuit-breaker fallback endpoint (Phase 5B). When a route's Resilience4j breaker is open (its
 * downstream service is failing or timing out), the Gateway forwards the request here instead of the
 * dead service; each route's {@code fallbackUri} points at {@code /fallback/<service>}. We answer with a
 * 503 in the standard {@link ApiError} envelope so the caller sees a consistent, machine-readable
 * shape rather than a raw gateway error. Reactive ({@code Mono}) — this is a WebFlux app.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/{service}")
    public Mono<ResponseEntity<ApiError>> get(@PathVariable String service, ServerWebExchange exchange) {
        return fallback(service, exchange);
    }

    @PostMapping("/{service}")
    public Mono<ResponseEntity<ApiError>> post(@PathVariable String service, ServerWebExchange exchange) {
        return fallback(service, exchange);
    }

    private Mono<ResponseEntity<ApiError>> fallback(@PathVariable String service, ServerWebExchange exchange) {
        ApiError body =
                ApiError.of(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                        service + " is temporarily unavailable; please retry shortly.",
                        exchange.getRequest().getPath().value());
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
