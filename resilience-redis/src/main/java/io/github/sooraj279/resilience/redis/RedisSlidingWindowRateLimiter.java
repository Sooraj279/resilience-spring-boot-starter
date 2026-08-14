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
import java.util.UUID;

public class RedisSlidingWindowRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisSlidingWindowRateLimiter.class);

    private final StringRedisTemplate redis;
    private final RedisScript<List> script;
    private final String name;
    private final long limit;
    private final long windowMillis;
    private final boolean failOpen;

    public RedisSlidingWindowRateLimiter(StringRedisTemplate redis, String name,
                                         long limit, long windowMillis, boolean failOpen) {
        this.redis = redis;
        this.name = name;
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.failOpen = failOpen;

        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setLocation(new ClassPathResource("scripts/sliding_window.lua"));
        s.setResultType(List.class);
        this.script = s;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitResult tryAcquire(String key) {
        try {
            List<Long> result = redis.execute(script,
                    Collections.singletonList("ratelimit:sw:" + name + ":" + key),
                    String.valueOf(limit),
                    String.valueOf(windowMillis),
                    String.valueOf(System.currentTimeMillis()),
                    UUID.randomUUID().toString());

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
        log.warn("Redis sliding window unavailable ({}), failing {}", reason, failOpen ? "open" : "closed");
        return failOpen ? RateLimitResult.allow(-1) : RateLimitResult.reject(1000);
    }
}