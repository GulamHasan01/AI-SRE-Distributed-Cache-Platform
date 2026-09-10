package com.cache.eviction;

import com.cache.model.CacheEntry;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public class LFUEvictionPolicy implements EvictionPolicy {

    @Override
    public Optional<String> selectVictim(Collection<CacheEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }

        return entries.stream()
                .min(Comparator.comparingLong(CacheEntry::getAccessCount)
                        .thenComparing(CacheEntry::getLastAccessed))
                .map(CacheEntry::getKey);
    }

    @Override
    public String policyName() {
        return "LFU";
    }
}
