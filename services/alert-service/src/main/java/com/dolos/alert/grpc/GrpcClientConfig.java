package com.dolos.alert.grpc;

import com.dolos.proto.scoring.ScoringServiceGrpc;
import com.dolos.proto.scoring.ScoringServiceGrpc.ScoringServiceBlockingStub;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC client wiring for the scoring ScoringService (Phase 2C). A single managed channel to
 * scoring-service (target defaults to localhost:9090; the container overrides it to
 * scoring-service:9090) backs a blocking stub. Plaintext is fine on the trusted internal Docker
 * network — TLS arrives with the gateway in Phase 5.
 */
@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel scoringChannel(
            @Value("${dolos.scoring.grpc.target:localhost:9090}") String target) {
        return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
    }

    @Bean
    public ScoringServiceBlockingStub scoringStub(ManagedChannel scoringChannel) {
        return ScoringServiceGrpc.newBlockingStub(scoringChannel);
    }
}
