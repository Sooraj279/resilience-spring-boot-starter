package io.github.sooraj279.resilience.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding window log: stores one timestamp per request and count those still
 * inside the window. Exact, but memory grows with request rate - the opposite
 * tradeoff to the token bucket's fixed two fields per key.
 */

public class SlidingWindowRateLimiter implements RateLimiter {

    private final long limit;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Deque<Long>> window = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(long limit, long windowMillis){
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    @Override
    public RateLimitResult tryAcquire(String key){
        Deque<Long> timestamps = window.computeIfAbsent(key, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis;

        synchronized (timestamps){
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff){
                timestamps.pollFirst();
            }
            if (timestamps.size() < limit){
                timestamps.addLast(now);
                return RateLimitResult.allow(limit - timestamps.size());
            }
            long oldest = timestamps.peekFirst();
            return RateLimitResult.reject((oldest + windowMillis) - now);
        }
    }
}
