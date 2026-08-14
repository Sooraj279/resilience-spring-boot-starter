package io.github.sooraj279.resilience.core.circuitbreaker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CircuitBreakerRegistry {

    private final ConcurrentHashMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    public CircuitBreaker get(String name, int failureThreshold, long openTimeoutMillis){
        return breakers.computeIfAbsent(name, n -> new CircuitBreaker(failureThreshold,openTimeoutMillis));
    }

    //Snapshot for metrics and diagnostics.
    public Map<String, CircuitBreaker> snapshot(){
        return Map.copyOf(breakers);
    }
}
