package io.github.sooraj279.resilience.autoconfigure.web;

import io.github.sooraj279.resilience.core.ratelimit.RateLimitResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the {@code X-RateLimit-*} and {@code Retry-After} headers onto the
 * servlet response bound to the current thread. Silently does nothing outside
 * an HTTP request — a scheduled job, for instance.
 */
public class ServletRateLimitHeaderWriter implements RateLimitHeaderWriter {

    @Override
    public void writeHeaders(long limit, RateLimitResult result) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        HttpServletResponse response = attrs.getResponse();
        if (response == null) {
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        if (result.remaining() >= 0) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        }
        if (!result.allowed()) {
            long seconds = Math.max(1, result.retryAfterMillis() / 1000);
            response.setHeader("Retry-After", String.valueOf(seconds));
        }
    }
}
