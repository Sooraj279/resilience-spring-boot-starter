package io.github.sooraj279.resilience.autoconfigure;

import io.github.sooraj279.resilience.autoconfigure.aspect.CircuitBreakerAspect;
import io.github.sooraj279.resilience.autoconfigure.aspect.RateLimitAspect;
import io.github.sooraj279.resilience.autoconfigure.exception.ResilienceExceptionHandler;
import io.github.sooraj279.resilience.autoconfigure.web.RateLimitHeaderWriter;
import io.github.sooraj279.resilience.core.circuitbreaker.CircuitBreakerRegistry;
import io.github.sooraj279.resilience.core.ratelimit.InMemoryRateLimiterFactory;
import io.github.sooraj279.resilience.core.ratelimit.RateLimiterFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ResilienceAutoConfiguration.class));

    @Test
    void registersInMemoryFactoryByDefault() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(RateLimiterFactory.class)
                .getBean(RateLimiterFactory.class)
                .isInstanceOf(InMemoryRateLimiterFactory.class));
    }

    @Test
    void backsOffWhenDisabled() {
        runner.withPropertyValues("resilience.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RateLimiterFactory.class));
    }

    @Test
    void userDefinedFactoryTakesPrecedence() {
        runner.withBean(RateLimiterFactory.class, () -> (name, spec) -> key -> null)
                .run(context -> assertThat(context)
                        .getBean(RateLimiterFactory.class)
                        .isNotInstanceOf(InMemoryRateLimiterFactory.class));
    }

    @Test
    void registersWebBeansWhenSpringWebIsPresent() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(ResilienceExceptionHandler.class)
                .hasSingleBean(RateLimitHeaderWriter.class));
    }

    /**
     * Micrometer is {@code optional} here, so it is absent from the classpath of
     * any downstream module that does not depend on it — resilience-redis, for
     * one. Spring reflects over the whole auto-configuration class, so a method
     * mentioning MeterRegistry used to blow it up with a NoClassDefFoundError
     * before any condition was consulted.
     */
    @Test
    void loadsWithoutMicrometer() {
        runner.withClassLoader(new FilteredClassLoader("io.micrometer.core"))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(RateLimiterFactory.class)
                        .hasSingleBean(CircuitBreakerRegistry.class)
                        .hasSingleBean(RateLimitAspect.class)
                        .hasSingleBean(CircuitBreakerAspect.class)
                        .doesNotHaveBean(ResilienceMetrics.class));
    }

    /** Same hazard, for the Spring Web and Servlet types. */
    @Test
    void loadsWithoutSpringWebOrServletApi() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.web", "jakarta.servlet"))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(RateLimiterFactory.class)
                        .hasSingleBean(RateLimitAspect.class)
                        .doesNotHaveBean(ResilienceExceptionHandler.class)
                        .doesNotHaveBean(RateLimitHeaderWriter.class));
    }

    /** The exact classpath resilience-redis sees: no Micrometer, no web, no servlet. */
    @Test
    void loadsOnADownstreamModuleClasspath() {
        runner.withClassLoader(new FilteredClassLoader(
                        "io.micrometer.core", "org.springframework.web", "jakarta.servlet"))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(RateLimiterFactory.class)
                        .hasSingleBean(CircuitBreakerRegistry.class));
    }
}
