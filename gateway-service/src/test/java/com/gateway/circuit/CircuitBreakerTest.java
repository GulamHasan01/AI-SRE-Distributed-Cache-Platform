package com.gateway.circuit;

import com.gateway.filter.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Gateway Resilience: CircuitBreaker & RateLimiter")
class CircuitBreakerTest {

    @Test
    @DisplayName("CircuitBreaker starts in CLOSED state and allows requests")
    void initialStateIsClosed() {
        CircuitBreaker cb = new CircuitBreaker("node-1", 3, Duration.ofMillis(200));
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("CircuitBreaker trips to OPEN after failure threshold reached")
    void tripsToOpenOnConsecutiveFailures() {
        CircuitBreaker cb = new CircuitBreaker("node-1", 3, Duration.ofMillis(300));

        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();

        cb.recordFailure(); // 3rd failure hits threshold
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();
    }

    @Test
    @DisplayName("CircuitBreaker transitions to HALF_OPEN after reset timeout")
    void transitionsToHalfOpenAfterTimeout() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker("node-1", 2, Duration.ofMillis(50));

        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(70);
        assertThat(cb.allowRequest()).isTrue();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Success closes it
        cb.recordSuccess();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getConsecutiveFailures()).isEqualTo(0);
    }

    @Test
    @DisplayName("RateLimiter enforces token bucket capacity and rate")
    void rateLimiterEnforcesCapacity() {
        // 0.1 tokens/sec = 1 token every 10 seconds, prevents refill during test
        RateLimiter limiter = new RateLimiter(3, 0.1);

        assertThat(limiter.tryAcquire("client-1")).isTrue();
        assertThat(limiter.tryAcquire("client-1")).isTrue();
        assertThat(limiter.tryAcquire("client-1")).isTrue();
        // 4th should exceed capacity
        assertThat(limiter.tryAcquire("client-1")).isFalse();

        // Distinct clients have independent buckets
        assertThat(limiter.tryAcquire("client-2")).isTrue();
    }
}
