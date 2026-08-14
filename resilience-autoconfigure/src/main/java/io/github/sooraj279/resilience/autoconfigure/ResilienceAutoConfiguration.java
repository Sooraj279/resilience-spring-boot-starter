package io.github.sooraj279.resilience.autoconfigure;

import io.github.sooraj279.resilience.autoconfigure.aspect.CircuitBreakerAspect;
import io.github.sooraj279.resilience.autoconfigure.aspect.RateLimitAspect;
import io.github.sooraj279.resilience.autoconfigure.exception.ResilienceExceptionHandler;
import io.github.sooraj279.resilience.autoconfigure.web.RateLimitHeaderWriter;
import io.github.sooraj279.resilience.autoconfigure.web.ServletRateLimitHeaderWriter;
import io.github.sooraj279.resilience.core.circuitbreaker.CircuitBreakerRegistry;
import io.github.sooraj279.resilience.core.ratelimit.InMemoryRateLimiterFactory;
import io.github.sooraj279.resilience.core.ratelimit.RateLimiterFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p><strong>Classpath rule for this class:</strong> Micrometer, Spring Web and
 * the Servlet API are all {@code optional} or {@code provided} dependencies of
 * this module, so none of them reach a downstream consumer that does not ask for
 * them itself. Spring reflects over this class as a whole - for instance to
 * deduce a bean type for a bare {@code @ConditionalOnMissingBean} - and
 * {@code Class.getDeclaredMethods()} resolves the parameter and return types of
 * <em>every</em> declared method at once. A single method mentioning an absent
 * type therefore fails the entire auto-configuration, and a method-level
 * {@code @ConditionalOnClass} does not save it, because the condition is never
 * reached.
 *
 * <p>So: no method declared directly on this class may name an optional type.
 * Those beans live in the nested configurations below, which are skipped from
 * ASM metadata before they are ever loaded.
 */
@AutoConfiguration
@EnableConfigurationProperties(ResilienceProperties.class)
@ConditionalOnProperty(prefix = "resilience", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ResilienceAutoConfiguration {

    /**
     * Backs off if another factory bean (e.g. the Redis one) is already defined.
     * This is how the backend switch works.
     */
    @Bean
    @ConditionalOnMissingBean(RateLimiterFactory.class)
    public RateLimiterFactory rateLimiterFactory() {
        return new InMemoryRateLimiterFactory();
    }

    @Bean
    @ConditionalOnMissingBean(CircuitBreakerRegistry.class)
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return new CircuitBreakerRegistry();
    }

    @Bean
    public RateLimitAspect rateLimitAspect(RateLimiterFactory factory,
                                           ObjectProvider<ResilienceMetrics> metrics,
                                           ObjectProvider<RateLimitHeaderWriter> headerWriter) {
        return new RateLimitAspect(factory, metrics.getIfAvailable(), headerWriter.getIfAvailable());
    }

    @Bean
    public CircuitBreakerAspect circuitBreakerAspect(CircuitBreakerRegistry registry,
                                                     ObjectProvider<ResilienceMetrics> metrics) {
        return new CircuitBreakerAspect(registry, metrics.getIfAvailable());
    }

    /** Only when Micrometer is really on the classpath. See the class javadoc. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        @ConditionalOnMissingBean(ResilienceMetrics.class)
        ResilienceMetrics resilienceMetrics(MeterRegistry registry) {
            return new ResilienceMetrics(registry);
        }
    }

    /** Only when the Servlet API is really on the classpath. See the class javadoc. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "jakarta.servlet.http.HttpServletResponse",
            "org.springframework.web.context.request.RequestContextHolder"
    })
    static class ServletSupportConfiguration {

        @Bean
        @ConditionalOnMissingBean(RateLimitHeaderWriter.class)
        RateLimitHeaderWriter servletRateLimitHeaderWriter() {
            return new ServletRateLimitHeaderWriter();
        }
    }

    /** Only when Spring Web is really on the classpath. See the class javadoc. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.springframework.http.ResponseEntity",
            "org.springframework.web.bind.annotation.RestControllerAdvice"
    })
    static class WebExceptionHandlingConfiguration {

        @Bean
        @ConditionalOnMissingBean(ResilienceExceptionHandler.class)
        ResilienceExceptionHandler resilienceExceptionHandler() {
            return new ResilienceExceptionHandler();
        }
    }
}
