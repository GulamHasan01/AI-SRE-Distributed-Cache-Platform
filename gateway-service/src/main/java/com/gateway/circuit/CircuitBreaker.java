package com.gateway.circuit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe Circuit Breaker for downstream cache node resilience.
 * State transitions: CLOSED -> OPEN (on threshold consecutive failures) -> HALF_OPEN (after cooldown) -> CLOSED / OPEN.
 */
public class CircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final String nodeId;
    private final int failureThreshold;
    private final Duration resetTimeout;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> lastStateChange = new AtomicReference<>(Instant.now());

    public CircuitBreaker(String nodeId, int failureThreshold, Duration resetTimeout) {
        this.nodeId = nodeId;
        this.failureThreshold = failureThreshold;
        this.resetTimeout = resetTimeout;
    }

    public boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }

        if (current == State.OPEN) {
            Instant openedAt = lastStateChange.get();
            if (Duration.between(openedAt, Instant.now()).compareTo(resetTimeout) >= 0) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    lastStateChange.set(Instant.now());
                    return true;
                }
            }
            return false;
        }

        // HALF_OPEN: allow trial request
        return true;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        if (state.get() == State.HALF_OPEN) {
            state.set(State.CLOSED);
            lastStateChange.set(Instant.now());
        }
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (state.get() == State.HALF_OPEN || failures >= failureThreshold) {
            state.set(State.OPEN);
            lastStateChange.set(Instant.now());
        }
    }

    public State getState() {
        // Trigger lazy transition to HALF_OPEN if resetTimeout has elapsed
        if (state.get() == State.OPEN) {
            Instant openedAt = lastStateChange.get();
            if (Duration.between(openedAt, Instant.now()).compareTo(resetTimeout) >= 0) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
            }
        }
        return state.get();
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
}
