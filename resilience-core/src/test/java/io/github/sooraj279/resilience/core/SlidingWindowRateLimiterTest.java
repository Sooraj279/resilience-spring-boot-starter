package io.github.sooraj279.resilience.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class SlidingWindowRateLimiterTest {
    @Test
    void  neverAllowsMoreThanCapacityUnderConcurrentLoad() throws InterruptedException{
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, 10000);

        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();                   // all threads released together
                    if (limiter.tryAcquire("user-1").allowed()) {
                        allowed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue(
                done.await(5, TimeUnit.SECONDS),
                "Timed out waiting for concurrent requests"
        );
        pool.shutdown();

        assertEquals(
                3,
                allowed.get(),
                "Exactly 3 requests should be allowed"
        );
    }

    @Test
    void reportsRetryAfterWhenExhausted() {

        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, 10000);

        assertTrue(limiter.tryAcquire("u").allowed());

        RateLimitResult rejected = limiter.tryAcquire("u");
        assertFalse(rejected.allowed());
        assertTrue(rejected.retryAfterMillis() > 0);
    }

}

