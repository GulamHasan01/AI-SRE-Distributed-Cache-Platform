package com.cache.store;

import com.cache.model.CacheEntry;

import java.util.Collection;
import java.util.Optional;

public interface CacheStore {

    void put(CacheEntry entry);

    Optional<CacheEntry> get(String key);

    boolean delete(String key);

    long clear();

    int removeExpired();

    long size();

    boolean isEmpty();

    boolean containsKey(String key);

    Collection<CacheEntry> getAllEntries();

    long getEvictionCount();

    long getExpiredCount();

    void setEvictionPolicy(com.cache.eviction.EvictionPolicy policy);

    com.cache.eviction.EvictionPolicy getEvictionPolicy();
}
