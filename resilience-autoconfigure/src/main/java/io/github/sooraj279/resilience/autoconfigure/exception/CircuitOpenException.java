package io.github.sooraj279.resilience.autoconfigure.exception;

public class CircuitOpenException extends RuntimeException {

    public CircuitOpenException(String circuitName) {
        super("Circuit open for " + circuitName);
    }
}