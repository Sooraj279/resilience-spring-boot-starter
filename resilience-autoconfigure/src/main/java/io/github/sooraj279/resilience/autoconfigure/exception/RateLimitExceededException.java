package io.github.sooraj279.resilience.autoconfigure.exception;

public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterMillis;

    public RateLimitExceededException(String limiterName, long retryAfterMillis) {
        super("Rate limit exceeded for " + limiterName);
        this.retryAfterMillis = retryAfterMillis;
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }
}