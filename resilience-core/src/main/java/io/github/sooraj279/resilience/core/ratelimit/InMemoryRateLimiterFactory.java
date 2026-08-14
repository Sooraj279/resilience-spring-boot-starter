package io.github.sooraj279.resilience.core.ratelimit;

import java.util.concurrent.TimeUnit;

public class InMemoryRateLimiterFactory implements RateLimiterFactory{

    @Override
    public RateLimiter create(String name, RateLimitSpec spec){
        return switch (spec.algorithm()){
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(
                    spec.capacity(), spec.refillTokens(), spec.refillPeriodSeconds(), TimeUnit.SECONDS
            );
            case SLIDING_WINDOW -> new SlidingWindowRateLimiter(
                    spec.capacity(), spec.refillPeriodSeconds()*1000
            );
        };
    }
}
