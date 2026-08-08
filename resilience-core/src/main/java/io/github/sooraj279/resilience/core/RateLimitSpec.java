package io.github.sooraj279.resilience.core;

/**
 * Configuration for one named limiter. For SLIDING_WINDOW, {@code capacity}
 * is max requests per window and {@code refillPeriodSecond} is the window length.
 */


public record RateLimitSpec(
        long capacity,
        long refillTokens,
        long refillPeriodSeconds,
        Algorithm algorithm) {
}
