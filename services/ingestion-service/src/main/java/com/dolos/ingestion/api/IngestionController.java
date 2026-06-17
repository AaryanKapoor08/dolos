package com.dolos.ingestion.api;

import com.dolos.ingestion.api.dto.IngestAccepted;
import com.dolos.ingestion.api.dto.IngestTransactionRequest;
import com.dolos.ingestion.service.IngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive ingest API.
 *
 * <ul>
 *   <li>{@code POST /ingest/transactions} — a single transaction (JSON).</li>
 *   <li>{@code POST /ingest/transactions} (NDJSON) — a stream of transactions. Bounded-concurrency
 *       {@code flatMap} caps in-flight work, so a fast producer cannot overwhelm the DB/broker:
 *       WebFlux propagates backpressure up the request body as demand is throttled downstream.</li>
 * </ul>
 */
@RestController
@RequestMapping("/ingest/transactions")
public class IngestionController {

    /** Max transactions processed concurrently from a stream — the backpressure bound. */
    private static final int MAX_IN_FLIGHT = 16;

    private final IngestionService service;

    public IngestionController(IngestionService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<IngestAccepted> ingest(@Valid @RequestBody IngestTransactionRequest request) {
        return service.ingest(request).map(IngestAccepted::of);
    }

    @PostMapping(consumes = MediaType.APPLICATION_NDJSON_VALUE, produces = MediaType.APPLICATION_NDJSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Flux<IngestAccepted> ingestStream(@RequestBody Flux<IngestTransactionRequest> requests) {
        return requests.flatMap(req -> service.ingest(req).map(IngestAccepted::of), MAX_IN_FLIGHT);
    }
}
