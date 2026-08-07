package io.github.sooraj279.resilience.core;

public interface RateLimiter {
    /**
     * @param key identifies who is being rate limited — a user ID, an API key, an IP, etc.
     * @return true if the request is allowed, false if the caller should be rejected
     */
    boolean tryAcquire(String key);
}
