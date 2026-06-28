package com.dolos.alert.grpc;

import com.dolos.proto.scoring.ScoreDetail;
import com.dolos.proto.scoring.ScoreDetailRequest;
import com.dolos.proto.scoring.ScoringServiceGrpc.ScoringServiceBlockingStub;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Synchronously fetches a transaction's full score detail from scoring-service over gRPC (Phase 2C),
 * wrapped in Resilience4j: a per-call <b>timeout</b> (the gRPC deadline), bounded <b>retry</b>, and a
 * <b>circuit breaker</b> with a <b>fallback</b> to "details unavailable". So if scoring-service is
 * slow or down, the breaker trips and alert-service still raises the alert — just without the detail.
 */
@Component
public class ScoreDetailClient {

    private static final Logger log = LoggerFactory.getLogger(ScoreDetailClient.class);
    private static final String BACKEND = "scoring";

    private final ScoringServiceBlockingStub stub;
    private final long deadlineMs;

    public ScoreDetailClient(
            ScoringServiceBlockingStub stub,
            @Value("${dolos.scoring.grpc.deadline-ms:500}") long deadlineMs) {
        this.stub = stub;
        this.deadlineMs = deadlineMs;
    }

    @CircuitBreaker(name = BACKEND, fallbackMethod = "fallback")
    @Retry(name = BACKEND)
    public ScoreDetailView getScoreDetails(UUID transactionId) {
        ScoreDetail detail =
                stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                        .getScoreDetails(
                                ScoreDetailRequest.newBuilder()
                                        .setTransactionId(transactionId.toString())
                                        .build());
        String summary = detail.getFound() ? detail.getSummary() : "no score detail on record";
        return new ScoreDetailView(detail.getFound(), summary);
    }

    /** Resilience4j fallback — same signature plus the Throwable. Keeps the alert flowing. */
    @SuppressWarnings("unused")
    private ScoreDetailView fallback(UUID transactionId, Throwable t) {
        log.warn(
                "scoring gRPC unavailable for txn {} ({}) — using fallback detail",
                transactionId,
                t.toString());
        return ScoreDetailView.unavailable();
    }
}
