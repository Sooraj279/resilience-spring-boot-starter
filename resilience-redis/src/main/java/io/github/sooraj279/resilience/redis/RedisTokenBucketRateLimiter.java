package io.github.sooraj279.resilience.redis;

import io.github.sooraj279.resilience.core.ratelimit.RateLimitResult;
import io.github.sooraj279.resilience.core.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

public class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

    private final StringRedisTemplate redis;
    private final RedisScript<List> script;
    private final String name;
    private final long capacity;
    private final long refillTokens;
    private final long refillPeriodSeconds;
    private final boolean failOpen;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis, String name,
                                       long capacity, long refillTokens,
                                       long refillPeriodSeconds, boolean failOpen) {
        this.redis = redis;
        this.name = name;
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodSeconds = refillPeriodSeconds;
        this.failOpen = failOpen;

        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        s.setResultType(List.class);
        this.script = s;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitResult tryAcquire(String key) {
        try {
            List<Long> result = redis.execute(script,
                    Collections.singletonList("ratelimit:tb:" + name + ":" + key),
                    String.valueOf(capacity),
                    String.valueOf(refillTokens),
                    String.valueOf(refillPeriodSeconds),
                    String.valueOf(System.currentTimeMillis()),
                    "1");

            if (result == null || result.size() < 3) {
                return degraded("unexpected script result");
            }
            return result.get(0) == 1L
                    ? RateLimitResult.allow(result.get(1))
                    : RateLimitResult.reject(result.get(2));

        } catch (Exception e) {
            return degraded(e.getMessage());
        }
    }

    private RateLimitResult degraded(String reason) {
        log.warn("Redis rate limiter unavailable ({}), failing {}", reason, failOpen ? "open" : "closed");
        return failOpen ? RateLimitResult.allow(-1) : RateLimitResult.reject(1000);
    }
}