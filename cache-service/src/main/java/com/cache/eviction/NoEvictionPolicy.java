package com.cache.eviction;

import com.cache.model.CacheEntry;

import java.util.Collection;
import java.util.Optional;

public class NoEvictionPolicy implements EvictionPolicy {

    @Override
    public Optional<String> selectVictim(Collection<CacheEntry> entries) {
        return Optional.empty();
    }

    @Override
    public String policyName() {
        return "NO_EVICTION";
    }
}
