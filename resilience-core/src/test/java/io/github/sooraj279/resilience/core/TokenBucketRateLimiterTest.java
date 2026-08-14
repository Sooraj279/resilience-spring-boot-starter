package io.github.sooraj279.resilience.core;

import io.github.sooraj279.resilience.core.ratelimit.RateLimitResult;
import io.github.sooraj279.resilience.core.ratelimit.TokenBucketRateLimiter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest {
    @Test
    void allowsNoMoreThanCapacityEvenUnderConcurrentLoad() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 10, 1, TimeUnit.SECONDS);

        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++){
            pool.submit(() -> {
               ready.countDown();
               try {
                   start.await(); // all threads released together
                   if (limiter.tryAcquire("user-1").allowed()){
                           allowed.incrementAndGet();
                   }
               } catch (InterruptedException e){
                   Thread.currentThread().interrupt();
               } finally {
                   done.countDown();
               }
            });
        }

        ready.await();
        start.countDown();
        done.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(allowed.get() <= 10, "capacity is 10, got "+ allowed.get());
    }

    @Test
    void reportsRetryAfterWhenExhausted(){
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1,1, 10,TimeUnit.SECONDS);

        assertTrue(limiter.tryAcquire("u").allowed());

        RateLimitResult rejected = limiter.tryAcquire("u");
        assertFalse(rejected.allowed());
        assertTrue(rejected.retryAfterMillis()>0);
    }
}
