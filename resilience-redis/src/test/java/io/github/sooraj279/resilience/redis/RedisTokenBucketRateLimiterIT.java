package io.github.sooraj279.resilience.redis;

import io.github.sooraj279.resilience.core.ratelimit.RateLimitResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes = RedisTokenBucketRateLimiterIT.TestApp.class)
class RedisTokenBucketRateLimiterIT {

    @SpringBootApplication
    static class TestApp { }

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    void enforcesLimitAndReportsRetryAfter() {
        String key = UUID.randomUUID().toString();
        RedisTokenBucketRateLimiter limiter =
                new RedisTokenBucketRateLimiter(redisTemplate, "test", 3, 3, 60, false);

        assertTrue(limiter.tryAcquire(key).allowed());
        assertTrue(limiter.tryAcquire(key).allowed());
        assertTrue(limiter.tryAcquire(key).allowed());

        RateLimitResult rejected = limiter.tryAcquire(key);
        assertFalse(rejected.allowed());
        assertTrue(rejected.retryAfterMillis() > 0);
    }
}