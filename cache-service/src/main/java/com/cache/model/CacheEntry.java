package com.cache.model;

import java.time.Instant;

public class CacheEntry {

    private final String key;
    private final String value;
    private final Instant createdAt;
    private volatile Instant lastAccessed;
    private volatile long accessCount;

    private final long ttlSeconds;

    public CacheEntry(String key, String value) {
        this(key, value, -1L);
    }

    public CacheEntry(String key, String value, long ttlSeconds) {
        this.key = key;
        this.value = value;
        this.createdAt = Instant.now();
        this.lastAccessed = Instant.now();
        this.accessCount = 0;
        this.ttlSeconds = ttlSeconds;
    }

    public boolean isExpired() {
        if (ttlSeconds <= 0) {
            return false;
        }
        return Instant.now().isAfter(createdAt.plusSeconds(ttlSeconds));
    }

    public long getRemainingTtlSeconds() {
        if (ttlSeconds <= 0) return -1;
        long elapsed = Instant.now().getEpochSecond() - createdAt.getEpochSecond();
        long remaining = ttlSeconds - elapsed;
        return Math.max(0, remaining);
    }

    public synchronized void recordAccess() {
        this.lastAccessed = Instant.now();
        this.accessCount++;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessed() { return lastAccessed; }
    public long getAccessCount() { return accessCount; }
    public long getTtlSeconds() { return ttlSeconds; }
    public boolean hasTtl() { return ttlSeconds > 0; }

    @Override
    public String toString() {
        return "CacheEntry{key='" + key + "', ttl=" + ttlSeconds + "s, expired=" + isExpired() + "}";
    }
}
