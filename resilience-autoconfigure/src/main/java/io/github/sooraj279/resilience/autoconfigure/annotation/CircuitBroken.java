package io.github.sooraj279.resilience.autoconfigure.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitBroken {

    /** Consecutive failures before the circuit opens. */
    int failureThreshold() default 5;

    /** How long the circuit stays open before allowing a trial call. */
    long openTimeoutMillis() default 10_000;
}