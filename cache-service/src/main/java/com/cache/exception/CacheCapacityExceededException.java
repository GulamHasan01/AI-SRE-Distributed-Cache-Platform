package com.cache.exception;

public class CacheCapacityExceededException extends RuntimeException {

    private final int maxCapacity;
    private final long currentSize;

    public CacheCapacityExceededException(int maxCapacity, long currentSize) {
        super(String.format(
                "Cache capacity exceeded. Max: %d, Current: %d. " +
                "No eviction policy is configured — upgrade to Phase 2 for LRU support.",
                maxCapacity, currentSize
        ));
        this.maxCapacity = maxCapacity;
        this.currentSize = currentSize;
    }

    public CacheCapacityExceededException(String message) {
        super(message);
        this.maxCapacity = 0;
        this.currentSize = 0;
    }

    public int getMaxCapacity() { return maxCapacity; }
    public long getCurrentSize() { return currentSize; }
}
