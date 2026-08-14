package io.github.sooraj279.resilience.autoconfigure.aspect;

import io.github.sooraj279.resilience.autoconfigure.exception.CircuitOpenException;
import io.github.sooraj279.resilience.autoconfigure.ResilienceMetrics;
import io.github.sooraj279.resilience.autoconfigure.annotation.CircuitBroken;
import io.github.sooraj279.resilience.core.circuitbreaker.CircuitBreaker;
import io.github.sooraj279.resilience.core.circuitbreaker.CircuitBreakerRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

@Aspect
public class CircuitBreakerAspect {

    private final CircuitBreakerRegistry registry;
    private final ResilienceMetrics metrics; // may be null

    public CircuitBreakerAspect(CircuitBreakerRegistry registry, ResilienceMetrics metrics) {
        this.registry = registry;
        this.metrics = metrics;
    }

    @Around("@annotation(circuitBroken)")
    public Object enforce(ProceedingJoinPoint joinPoint, CircuitBroken circuitBroken) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String name = method.getDeclaringClass().getSimpleName() + "#" + method.getName();

        CircuitBreaker breaker = registry.get(name,
                circuitBroken.failureThreshold(), circuitBroken.openTimeoutMillis());

        if (!breaker.allowRequest()) {
            if (metrics != null) metrics.recordCircuitRejected(name);
            throw new CircuitOpenException(name);
        }

        try {
            Object result = joinPoint.proceed();
            breaker.recordSuccess();
            return result;
        } catch (Throwable failure) {
            breaker.recordFailure();
            throw failure;
        }
    }
}