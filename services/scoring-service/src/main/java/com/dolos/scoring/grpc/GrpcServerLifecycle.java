package com.dolos.scoring.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Owns the embedded gRPC {@link Server} lifecycle (Phase 2C), tied to the Spring context so it starts
 * after the beans are ready and shuts down gracefully. Listens on a dedicated port (default 9090) —
 * separate from the 8083 HTTP/Actuator port — serving {@link ScoringGrpcService}.
 */
@Component
public class GrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final int port;
    private final ScoringGrpcService scoringGrpcService;

    private Server server;
    private volatile boolean running;

    public GrpcServerLifecycle(
            @Value("${dolos.grpc.port:9090}") int port, ScoringGrpcService scoringGrpcService) {
        this.port = port;
        this.scoringGrpcService = scoringGrpcService;
    }

    @Override
    public void start() {
        try {
            server = ServerBuilder.forPort(port).addService(scoringGrpcService).build().start();
            running = true;
            log.info("gRPC ScoringService listening on port {}", port);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            try {
                server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running = false;
                log.info("gRPC ScoringService stopped");
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
