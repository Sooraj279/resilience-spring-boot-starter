package io.github.sooraj279.resilience.core;

/**
 * Creates limiters. This indirection is what lets one aspect run against
 * either the in-memory or Redis backend without knowing which is active.
 * Spring decides at startup which implementation gets injected
 */
public interface RateLimiterFactory {
    RateLimiter create(String name, RateLimitSpec spec);
}
