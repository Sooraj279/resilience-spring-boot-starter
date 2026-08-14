package io.github.sooraj279.resilience.autoconfigure.annotation;

import io.github.sooraj279.resilience.core.ratelimit.Algorithm;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

    /** SpEL expression resolving the limit key, e.g. "#userId". */
    String key();

    long capacity() default 100;

    long refillTokens() default 100;

    long refillPeriodSeconds() default 60;

    Algorithm algorithm() default Algorithm.TOKEN_BUCKET;
}