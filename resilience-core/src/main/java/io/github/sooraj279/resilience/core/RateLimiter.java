package io.github.sooraj279.resilience.core;

public interface RateLimiter {
    /**
     * @param key identifies who is being rate limited — a user ID, an API key, an IP, etc.
     * @return the rate-limit decision, remaining permits, and retry delay
     */
    RateLimitResult tryAcquire(String key);
}
