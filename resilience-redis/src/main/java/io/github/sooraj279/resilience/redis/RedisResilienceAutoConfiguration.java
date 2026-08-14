package io.github.sooraj279.resilience.redis;

import io.github.sooraj279.resilience.autoconfigure.ResilienceAutoConfiguration;
import io.github.sooraj279.resilience.autoconfigure.ResilienceProperties;
import io.github.sooraj279.resilience.core.ratelimit.RateLimiterFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(before = ResilienceAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(ResilienceProperties.class)
@ConditionalOnProperty(prefix = "resilience.rate-limit", name = "backend", havingValue = "redis")
public class RedisResilienceAutoConfiguration {

    @Bean
    public RateLimiterFactory redisRateLimiterFactory(StringRedisTemplate redis,
                                                      ResilienceProperties properties) {
        return new RedisRateLimiterFactory(redis, properties.getRateLimit().isFailOpen());
    }
}