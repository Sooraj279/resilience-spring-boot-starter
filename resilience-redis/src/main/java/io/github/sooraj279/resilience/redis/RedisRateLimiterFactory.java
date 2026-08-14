package io.github.sooraj279.resilience.redis;

import io.github.sooraj279.resilience.core.ratelimit.RateLimitSpec;
import io.github.sooraj279.resilience.core.ratelimit.RateLimiter;
import io.github.sooraj279.resilience.core.ratelimit.RateLimiterFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisRateLimiterFactory implements RateLimiterFactory {

    private final StringRedisTemplate redis;
    private final boolean failOpen;

    public RedisRateLimiterFactory(StringRedisTemplate redis, boolean failOpen) {
        this.redis = redis;
        this.failOpen = failOpen;
    }

    @Override
    public RateLimiter create(String name, RateLimitSpec spec) {
        return switch (spec.algorithm()) {
            case TOKEN_BUCKET -> new RedisTokenBucketRateLimiter(
                    redis, name, spec.capacity(), spec.refillTokens(), spec.refillPeriodSeconds(), failOpen);
            case SLIDING_WINDOW -> new RedisSlidingWindowRateLimiter(
                    redis, name, spec.capacity(), spec.refillPeriodSeconds() * 1000, failOpen);
        };
    }
}