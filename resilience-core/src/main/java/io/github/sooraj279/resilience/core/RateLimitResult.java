package io.github.sooraj279.resilience.core;

/**
 * The outcome of a rate limit check.
 *
 * @param allowed whether the caller may proceed
 * @param remaining permits left, or -1 when unknown (backend unavaiable)
 * @param retryAfterMillis how long until the caller could succeed; 0 when allowed
 */

public record RateLimitResult(boolean allowed, long remaining, long retryAfterMillis) {

    public static RateLimitResult allow(long remaining){
        return new RateLimitResult(true, remaining, 0L);
    }

    public static RateLimitResult reject(long retryAfterMillis){
        return new RateLimitResult(false, 0L, Math.max(retryAfterMillis, 1L));
    }
}
