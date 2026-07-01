package com.dolos.bench;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * 6F benchmark #2 — virtual vs platform threads on a blocking workload.
 *
 * <p>Dolos runs with {@code spring.threads.virtual.enabled=true}, so a blocking request handler runs on a
 * virtual thread instead of a bounded Tomcat worker. This models that: each benchmark invocation is one
 * "burst" of {@code taskCount} independent requests, every one of which blocks for {@code blockMillis}
 * (standing in for a slow downstream call — DB, gRPC, HTTP). We measure how many such bursts complete per
 * second on each executor.
 *
 * <ul>
 *   <li>{@code virtualThreads} — one virtual thread per task ({@link Executors#newVirtualThreadPerTaskExecutor}).
 *       A blocking {@code Thread.sleep} UNMOUNTS the virtual thread (it does not pin the carrier), so all
 *       {@code taskCount} tasks progress concurrently and a burst finishes in ~{@code blockMillis}.</li>
 *   <li>{@code platformPool} — a fixed pool of {@code POOL_SIZE} OS threads, the classic bounded worker
 *       model. When {@code taskCount > POOL_SIZE} the burst serializes into
 *       {@code ceil(taskCount / POOL_SIZE)} waves, so it takes proportionally longer.</li>
 * </ul>
 *
 * <p>The gap is the whole point of virtual threads for I/O-bound handlers: throughput under blocking
 * concurrency is limited by the downstream latency, not by a thread-pool ceiling.
 */
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VirtualThreadBenchmark {

    /** The bounded platform pool's size — deliberately far below {@code taskCount} so the ceiling bites. */
    private static final int POOL_SIZE = 50;

    /** Concurrent blocking "requests" per burst. */
    @Param({"500"})
    private int taskCount;

    /**
     * How long each request blocks (models downstream latency). Swept deliberately: at ~1ms a
     * 50-thread pool already keeps up, so virtual threads barely win; as the block grows, pool
     * serialization dominates and the gap opens up. The sweep makes the crossover visible.
     */
    @Param({"1", "20"})
    private int blockMillis;

    private ExecutorService platformPool;

    @Setup
    public void setup() {
        platformPool = Executors.newFixedThreadPool(POOL_SIZE);
    }

    @TearDown
    public void tearDown() throws InterruptedException {
        platformPool.shutdown();
        platformPool.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Benchmark
    public void virtualThreads() throws InterruptedException {
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            runBurst(exec);
        }
    }

    @Benchmark
    public void platformPool() throws InterruptedException {
        runBurst(platformPool);
    }

    /** Submits {@code taskCount} blocking tasks and waits for the whole burst to drain. */
    private void runBurst(ExecutorService exec) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(taskCount);
        for (int i = 0; i < taskCount; i++) {
            exec.submit(() -> {
                block();
                done.countDown();
            });
        }
        done.await();
    }

    private void block() {
        try {
            Thread.sleep(blockMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
