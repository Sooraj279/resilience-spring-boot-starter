package io.github.sooraj279.resilience.autoconfigure.aspect;

import io.github.sooraj279.resilience.autoconfigure.exception.RateLimitExceededException;
import io.github.sooraj279.resilience.autoconfigure.ResilienceMetrics;
import io.github.sooraj279.resilience.autoconfigure.annotation.RateLimited;
import io.github.sooraj279.resilience.autoconfigure.web.RateLimitHeaderWriter;
import io.github.sooraj279.resilience.core.ratelimit.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
public class RateLimitAspect {

    private final RateLimiterFactory factory;
    private final ResilienceMetrics metrics;          // may be null
    private final RateLimitHeaderWriter headerWriter; // may be null

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer paramNames = new DefaultParameterNameDiscoverer();
    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    public RateLimitAspect(RateLimiterFactory factory,
                           ResilienceMetrics metrics,
                           RateLimitHeaderWriter headerWriter) {
        this.factory = factory;
        this.metrics = metrics;
        this.headerWriter = headerWriter;
    }

    @Around("@annotation(rateLimited)")
    public Object enforce(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String name = method.getDeclaringClass().getSimpleName() + "#" + method.getName();

        RateLimiter limiter = limiters.computeIfAbsent(name, n -> factory.create(n,
                new RateLimitSpec(
                        rateLimited.capacity(),
                        rateLimited.refillTokens(),
                        rateLimited.refillPeriodSeconds(),
                        rateLimited.algorithm())));

        String key = resolveKey(rateLimited.key(), method, joinPoint.getArgs());
        RateLimitResult result = limiter.tryAcquire(key);

        if (headerWriter != null) {
            headerWriter.writeHeaders(rateLimited.capacity(), result);
        }

        if (!result.allowed()) {
            if (metrics != null) metrics.recordRejected(name);
            throw new RateLimitExceededException(name, result.retryAfterMillis());
        }

        if (metrics != null) metrics.recordAllowed(name);
        return joinPoint.proceed();
    }

    private String resolveKey(String expression, Method method, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] names = paramNames.getParameterNames(method);
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                context.setVariable(names[i], args[i]);
            }
        }
        Object value = parser.parseExpression(expression).getValue(context);
        return value != null ? value.toString() : "default";
    }
}
