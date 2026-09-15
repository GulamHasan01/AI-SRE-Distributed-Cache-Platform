package com.gateway.filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory Token Bucket Rate Limiter for Gateway traffic shaping.
 * Tracks client tokens per IP address with burst capacity and continuous replenishment.
 */
public class RateLimiter {

    private final long capacity;
    private final double refillTokensPerSecond;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(long capacity, double refillTokensPerSecond) {
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
    }

    public boolean tryAcquire(String clientId) {
        return tryAcquire(clientId, 1);
    }

    public boolean tryAcquire(String clientId, long tokens) {
        Bucket bucket = buckets.computeIfAbsent(clientId, k -> new Bucket(capacity, refillTokensPerSecond));
        return bucket.tryConsume(tokens);
    }

    public void reset(String clientId) {
        buckets.remove(clientId);
    }

    public void clear() {
        buckets.clear();
    }

    private static class Bucket {
        private final long capacity;
        private final double refillRateNanos; // tokens per nanosecond
        private final AtomicLong availableTokens;
        private final AtomicLong lastRefillNanos;

        Bucket(long capacity, double refillTokensPerSecond) {
            this.capacity = capacity;
            this.refillRateNanos = refillTokensPerSecond / 1_000_000_000.0;
            this.availableTokens = new AtomicLong(capacity);
            this.lastRefillNanos = new AtomicLong(System.nanoTime());
        }

        synchronized boolean tryConsume(long tokens) {
            refill();
            long current = availableTokens.get();
            if (current >= tokens) {
                availableTokens.set(current - tokens);
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long last = lastRefillNanos.getAndSet(now);
            long elapsedNanos = Math.max(0, now - last);
            long tokensToAdd = (long) (elapsedNanos * refillRateNanos);

            if (tokensToAdd > 0) {
                availableTokens.updateAndGet(curr -> Math.min(capacity, curr + tokensToAdd));
            }
        }
    }
}
