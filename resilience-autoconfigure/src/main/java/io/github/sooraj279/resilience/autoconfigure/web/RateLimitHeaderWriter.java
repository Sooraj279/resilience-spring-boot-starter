package io.github.sooraj279.resilience.autoconfigure.web;

import io.github.sooraj279.resilience.core.ratelimit.RateLimitResult;

/**
 * Publishes rate-limit information onto the current transport's response.
 *
 * <p>Deliberately free of any servlet or Spring Web types so that
 * {@code RateLimitAspect} can be loaded in applications that have neither on
 * the classpath. Implementations are optional; when none is registered the
 * aspect simply skips header writing.
 */
public interface RateLimitHeaderWriter {

    /**
     * @param limit the configured capacity of the limiter
     * @param result the decision just made for the current call
     */
    void writeHeaders(long limit, RateLimitResult result);
}
