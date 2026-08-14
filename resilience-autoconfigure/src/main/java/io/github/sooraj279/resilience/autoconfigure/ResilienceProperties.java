package io.github.sooraj279.resilience.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "resilience")
public class ResilienceProperties {

    /** Master switch for the whole library. */
    private boolean enabled = true;

    private final RateLimit rateLimit = new RateLimit();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public RateLimit getRateLimit() { return rateLimit; }

    public static class RateLimit {

        /** "in-memory" or "redis". */
        private String backend = "in-memory";

        /**
         * When the Redis backend is unreachable: true allows requests through,
         * false rejects them. Defaults to fail-open — an outage in the limiter
         * should not become an outage in the application.
         */
        private boolean failOpen = true;

        public String getBackend() { return backend; }
        public void setBackend(String backend) { this.backend = backend; }
        public boolean isFailOpen() { return failOpen; }
        public void setFailOpen(boolean failOpen) { this.failOpen = failOpen; }
    }
}