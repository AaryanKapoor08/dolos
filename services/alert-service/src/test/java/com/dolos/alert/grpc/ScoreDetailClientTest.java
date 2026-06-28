package com.dolos.alert.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.dolos.proto.scoring.ScoreDetail;
import com.dolos.proto.scoring.ScoreDetailRequest;
import com.dolos.proto.scoring.ScoringServiceGrpc;
import com.dolos.proto.scoring.ScoringServiceGrpc.ScoringServiceBlockingStub;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the gRPC client mapping over a real in-process channel, plus the Resilience4j fallback
 * contract. The breaker/retry <em>wiring</em> (annotations firing the fallback under failure) is
 * proven by the Docker resilience run — here we pin the mapping and the fallback's return value.
 */
class ScoreDetailClientTest {

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    private ScoreDetailClient clientFor(ScoringServiceGrpc.ScoringServiceImplBase impl)
            throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(impl).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        ScoringServiceBlockingStub stub = ScoringServiceGrpc.newBlockingStub(channel);
        return new ScoreDetailClient(stub, 1000);
    }

    @Test
    void foundDetail_isMapped() throws IOException {
        ScoreDetailClient client =
                clientFor(
                        new ScoringServiceGrpc.ScoringServiceImplBase() {
                            @Override
                            public void getScoreDetails(
                                    ScoreDetailRequest request,
                                    StreamObserver<ScoreDetail> obs) {
                                obs.onNext(
                                        ScoreDetail.newBuilder()
                                                .setTransactionId(request.getTransactionId())
                                                .setFound(true)
                                                .setScore(70)
                                                .setSummary("2 rule(s) fired")
                                                .build());
                                obs.onCompleted();
                            }
                        });

        ScoreDetailView view = client.getScoreDetails(UUID.randomUUID());

        assertThat(view.found()).isTrue();
        assertThat(view.summary()).isEqualTo("2 rule(s) fired");
    }

    @Test
    void notFound_isMappedToNoDetail() throws IOException {
        ScoreDetailClient client =
                clientFor(
                        new ScoringServiceGrpc.ScoringServiceImplBase() {
                            @Override
                            public void getScoreDetails(
                                    ScoreDetailRequest request,
                                    StreamObserver<ScoreDetail> obs) {
                                obs.onNext(ScoreDetail.newBuilder().setFound(false).build());
                                obs.onCompleted();
                            }
                        });

        ScoreDetailView view = client.getScoreDetails(UUID.randomUUID());

        assertThat(view.found()).isFalse();
        assertThat(view.summary()).isEqualTo("no score detail on record");
    }

    @Test
    void fallback_returnsDetailsUnavailable() throws IOException {
        ScoreDetailClient client =
                clientFor(new ScoringServiceGrpc.ScoringServiceImplBase() {});

        ScoreDetailView view = client.fallback(UUID.randomUUID(), new RuntimeException("scoring down"));

        assertThat(view.found()).isFalse();
        assertThat(view.summary()).isEqualTo("details unavailable");
    }
}
