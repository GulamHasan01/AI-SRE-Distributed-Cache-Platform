package com.cache.exception;

public class CacheKeyNotFoundException extends RuntimeException {

    private final String key;

    public CacheKeyNotFoundException(String key) {
        super("Cache key not found: '" + key + "'");
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
