package com.dolos.scoring.grpc;

import com.dolos.events.RiskScored;
import com.dolos.proto.scoring.RiskScore;
import com.dolos.proto.scoring.ScoreDetail;
import com.dolos.proto.scoring.ScoreDetailRequest;
import com.dolos.proto.scoring.ScoringServiceGrpc;
import com.dolos.proto.scoring.Transaction;
import com.dolos.scoring.service.RiskScoringEngine;
import com.dolos.scoring.service.ScoreCache;
import com.dolos.scoring.service.ScoringFact;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The gRPC server side of {@code ScoringService} (Phase 2C). Two RPCs:
 *
 * <ul>
 *   <li>{@code GetScoreDetails} — looks up a previously produced score in the {@link ScoreCache}, so
 *       a consumer (alert-service) can synchronously enrich an alert with the full rule-hit detail
 *       that the lightweight Kafka event does not carry.
 *   <li>{@code Score} — an on-demand re-score of a supplied transaction through the Drools engine,
 *       with no historical window state (velocity/structuring need the streaming state stores).
 * </ul>
 */
@Component
public class ScoringGrpcService extends ScoringServiceGrpc.ScoringServiceImplBase {

    private final RiskScoringEngine engine;
    private final ScoreCache scoreCache;

    public ScoringGrpcService(RiskScoringEngine engine, ScoreCache scoreCache) {
        this.engine = engine;
        this.scoreCache = scoreCache;
    }

    @Override
    public void getScoreDetails(
            ScoreDetailRequest request, StreamObserver<ScoreDetail> responseObserver) {
        UUID transactionId;
        try {
            transactionId = UUID.fromString(request.getTransactionId());
        } catch (IllegalArgumentException badId) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("transaction_id is not a valid UUID")
                            .asRuntimeException());
            return;
        }

        ScoreDetail.Builder detail =
                ScoreDetail.newBuilder().setTransactionId(request.getTransactionId());
        scoreCache
                .get(transactionId)
                .ifPresentOrElse(
                        cached ->
                                detail.setAccountId(cached.accountId())
                                        .setScore(cached.score())
                                        .addAllReasons(cached.reasons())
                                        .setSummary(cached.reasons().size() + " rule(s) fired")
                                        .setFound(true),
                        () -> detail.setFound(false).setSummary("no score on record"));

        responseObserver.onNext(detail.build());
        responseObserver.onCompleted();
    }

    @Override
    public void score(Transaction request, StreamObserver<RiskScore> responseObserver) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(request.getAmount());
        } catch (NumberFormatException badAmount) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("amount is not a valid decimal")
                            .asRuntimeException());
            return;
        }

        UUID transactionId =
                request.getTransactionId().isEmpty()
                        ? UUID.randomUUID()
                        : UUID.fromString(request.getTransactionId());
        // On-demand score: no windowed history, so only single-transaction typologies can fire.
        ScoringFact fact =
                new ScoringFact(
                        transactionId,
                        request.getAccountId(),
                        amount,
                        emptyToNull(request.getCountry()),
                        Instant.now().toEpochMilli(),
                        emptyToNull(request.getCounterpartyAccountId()),
                        request.getDirection(),
                        1,
                        amount,
                        0,
                        BigDecimal.ZERO,
                        null,
                        null,
                        false);

        RiskScored scored = engine.score(fact);
        RiskScore response =
                RiskScore.newBuilder()
                        .setTransactionId(transactionId.toString())
                        .setAccountId(request.getAccountId())
                        .setScore(scored.score())
                        .addAllReasons(scored.reasons())
                        .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
