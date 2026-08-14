package io.github.sooraj279.demo;

import io.github.sooraj279.resilience.autoconfigure.annotation.CircuitBroken;
import io.github.sooraj279.resilience.autoconfigure.annotation.RateLimited;
import io.github.sooraj279.resilience.core.ratelimit.Algorithm;
import org.springframework.web.bind.annotation.*;

@RestController
public class DemoController {

    @GetMapping("/api/hello")
    @RateLimited(key = "#userId", capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    public String hello(@RequestParam String userId) {
        return "Hello, " + userId;
    }

    @GetMapping("/api/strict")
    @RateLimited(key = "#userId", capacity = 5, refillPeriodSeconds = 60,
            algorithm = Algorithm.SLIDING_WINDOW)
    public String strict(@RequestParam String userId) {
        return "Strict hello, " + userId;
    }

    /** Simulates a flaky downstream dependency so the circuit breaker is demonstrable. */
    @GetMapping("/api/flaky")
    @CircuitBroken(failureThreshold = 3, openTimeoutMillis = 10_000)
    public String flaky(@RequestParam(defaultValue = "false") boolean fail) {
        if (fail) {
            throw new IllegalStateException("downstream failure");
        }
        return "downstream ok";
    }
}