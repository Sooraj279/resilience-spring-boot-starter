package io.github.sooraj279.resilience.autoconfigure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.ConcurrentHashMap;

/** Thin Micrometer wrapper. Only created when Micrometer is on the classpath. */
public class ResilienceMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public ResilienceMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordAllowed(String limiter) {
        counter("resilience.ratelimit.allowed", limiter).increment();
    }

    public void recordRejected(String limiter) {
        counter("resilience.ratelimit.rejected", limiter).increment();
    }

    public void recordCircuitRejected(String circuit) {
        counter("resilience.circuit.rejected", circuit).increment();
    }

    private Counter counter(String metric, String name) {
        return counters.computeIfAbsent(metric + ":" + name,
                k -> Counter.builder(metric).tag("name", name).register(registry));
    }
}