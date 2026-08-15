package io.github.sooraj279.resilience.benchmarks;

import io.github.sooraj279.resilience.core.ratelimit.*;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class RateLimiterBenchmark {

    private RateLimiter tokenBucket;
    private RateLimiter slidingWindow;

    @Setup
    public void setup() {
        tokenBucket = new TokenBucketRateLimiter(10_000_000, 10_000_000, 1, TimeUnit.SECONDS);
        slidingWindow = new SlidingWindowRateLimiter(10_000_000, 1000);
    }

    @Benchmark
    @Threads(8)
    public RateLimitResult tokenBucket() {
        return tokenBucket.tryAcquire("bench");
    }

    @Benchmark
    @Threads(8)
    public RateLimitResult slidingWindow() {
        return slidingWindow.tryAcquire("bench");
    }
}