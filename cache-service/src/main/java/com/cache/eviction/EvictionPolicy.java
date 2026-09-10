package com.cache.eviction;

import com.cache.model.CacheEntry;

import java.util.Collection;
import java.util.Optional;

public interface EvictionPolicy {

    Optional<String> selectVictim(Collection<CacheEntry> entries);

    String policyName();
}
