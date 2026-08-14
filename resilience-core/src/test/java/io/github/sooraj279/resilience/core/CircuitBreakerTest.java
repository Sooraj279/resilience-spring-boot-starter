package io.github.sooraj279.resilience.core;

import io.github.sooraj279.resilience.core.circuitbreaker.CircuitBreaker;
import io.github.sooraj279.resilience.core.circuitbreaker.CircuitState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    @Test
    void tripsAfterThresholdAndRecoversAfterCooldown() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(3, 200);

        assertTrue(breaker.allowRequest());
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();

        assertEquals(CircuitState.OPEN, breaker.getState());
        assertFalse(breaker.allowRequest());

        Thread.sleep(250);

        assertTrue(breaker.allowRequest(), "cooldown elapsed — one trial should pass");
        assertEquals(CircuitState.HALF_OPEN, breaker.getState());
        assertFalse(breaker.allowRequest(), "a second concurrent trial must be held back");

        breaker.recordSuccess();
        assertEquals(CircuitState.CLOSED, breaker.getState());
    }
}