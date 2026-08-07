package io.github.sooraj279.resilience.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest {
    @Test
    void allowsNoMoreThanCapacityEvenUnderConcurrentLoad() throws InterruptedException{
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 10, 1, TimeUnit.SECONDS);

        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicInteger allowedCount = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(threadCount);

        for(int i = 0; i < threadCount; i++){
            pool.submit(() -> {
                if(limiter.tryAcquire("user-1")){
                    allowedCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(allowedCount.get() <= 10, "Bucket capacity is 10 — allowed count must never exceed it, got: " + allowedCount.get());
    }
}
