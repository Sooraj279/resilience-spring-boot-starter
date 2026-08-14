package io.github.sooraj279.resilience.core.circuitbreaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class CircuitBreaker {

    private final int failureThreshold;
    private final long openTimeoutMillis;

    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private final AtomicInteger consecutiveFailure = new AtomicInteger();
    private final AtomicLong openedAtMillis = new AtomicLong();

    public CircuitBreaker(int failureThreshold, long openTimeoutMillis){
        this.failureThreshold = failureThreshold;
        this.openTimeoutMillis = openTimeoutMillis;
    }

    public boolean allowRequest(){
        CircuitState current = state.get();

        if(current == CircuitState.CLOSED){
            return true;
        }

        if(current == CircuitState.OPEN){
            boolean coolDownElapsed = System.currentTimeMillis() - openedAtMillis.get() >= openTimeoutMillis;
            //compareAndSet: exactly one thread wins the single trial slot
            return coolDownElapsed && state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN);
        }

        //HALF_OPEN: a trial is already in flight - hold everyone else back
        return false;
    }

    public void recordSuccess(){
        consecutiveFailure.set(0);
        state.set(CircuitState.CLOSED);
    }

    public void recordFailure(){
        if (state.get() == CircuitState.HALF_OPEN){
            trip();
            return;
        }
        if (consecutiveFailure.incrementAndGet() >= failureThreshold){
            trip();
        }
    }

    private void trip(){
        openedAtMillis.set(System.currentTimeMillis());
        state.set(CircuitState.OPEN);
        consecutiveFailure.set(0);
    }

    public CircuitState getState(){
        return state.get();
    }
}
