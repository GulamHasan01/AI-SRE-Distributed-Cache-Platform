package com.cache.service.impl;

import com.cache.config.CacheProperties;
import com.cache.dto.request.CachePutRequest;
import com.cache.dto.response.CacheEntryResponse;
import com.cache.dto.response.CacheStatsResponse;
import com.cache.eviction.EvictionPolicy;
import com.cache.exception.CacheKeyNotFoundException;
import com.cache.model.CacheEntry;
import com.cache.service.CacheService;
import com.cache.store.CacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class CacheServiceImpl implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheServiceImpl.class);

    private final CacheStore cacheStore;
    private final CacheProperties cacheProperties;
    private final EvictionPolicy evictionPolicy;
    private final com.cache.cluster.replication.ReplicationService replicationService;

    private final AtomicLong totalHits = new AtomicLong(0);
    private final AtomicLong totalMisses = new AtomicLong(0);

    public CacheServiceImpl(CacheStore cacheStore,
                            CacheProperties cacheProperties,
                            EvictionPolicy evictionPolicy,
                            @org.springframework.context.annotation.Lazy com.cache.cluster.replication.ReplicationService replicationService) {
        this.cacheStore = cacheStore;
        this.cacheProperties = cacheProperties;
        this.evictionPolicy = evictionPolicy;
        this.replicationService = replicationService;
    }

    @Override
    public CacheEntryResponse put(CachePutRequest request) {
        long effectiveTtl = resolveEffectiveTtl(request.ttlSeconds());
        log.info("PUT key='{}' effectiveTtl={}s", request.key(), effectiveTtl);

        CacheEntry entry = new CacheEntry(request.key(), request.value(), effectiveTtl);
        cacheStore.put(entry);

        log.info("PUT successful: key='{}' ttl={}s", request.key(), effectiveTtl);
        return CacheEntryResponse.from(entry);
    }

    @Override
    public CacheEntryResponse get(String key) {
        log.debug("GET key='{}'", key);

        return cacheStore.get(key)
                .map(entry -> {
                    totalHits.incrementAndGet();
                    return CacheEntryResponse.from(entry);
                })
                .orElseThrow(() -> {
                    totalMisses.incrementAndGet();
                    return new CacheKeyNotFoundException(key);
                });
    }

    @Override
    public void delete(String key) {
        log.info("DELETE key='{}'", key);
        boolean removed = cacheStore.delete(key);
        if (!removed) {
            throw new CacheKeyNotFoundException(key);
        }
    }

    @Override
    public long clear() {
        log.info("CLEAR all entries");
        return cacheStore.clear();
    }

    @Override
    public CacheStatsResponse getStats() {
        long currentSize = cacheStore.size();
        int maxCapacity = cacheProperties.getMaxSize();
        long hits = totalHits.get();
        long misses = totalMisses.get();
        long totalRequests = hits + misses;

        double usagePercent = maxCapacity > 0
                ? round1dp((currentSize * 100.0) / maxCapacity)
                : 0.0;

        double hitRatio = totalRequests > 0
                ? round1dp((hits * 100.0) / totalRequests)
                : 0.0;

        return new CacheStatsResponse(
                cacheProperties.getNode().getId(),
                currentSize,
                maxCapacity,
                usagePercent,
                hits,
                misses,
                hitRatio,
                cacheStore.getEvictionCount(),
                cacheStore.getExpiredCount(),
                cacheStore.getEvictionPolicy().policyName(),
                replicationService.getStats()
        );
    }

    @Override
    public void updateEvictionPolicy(String policyName) {
        EvictionPolicy policy = switch (policyName.toUpperCase().trim()) {
            case "LRU" -> new com.cache.eviction.LRUEvictionPolicy();
            case "LFU" -> new com.cache.eviction.LFUEvictionPolicy();
            case "FIFO" -> new com.cache.eviction.FIFOEvictionPolicy();
            case "NO_EVICTION" -> new com.cache.eviction.NoEvictionPolicy();
            default -> throw new IllegalArgumentException("Unknown eviction policy: " + policyName);
        };
        cacheStore.setEvictionPolicy(policy);
        log.info("Eviction policy dynamically switched to: {}", policy.policyName());
    }

    private long resolveEffectiveTtl(Long requestTtl) {
        if (requestTtl != null && requestTtl > 0) {
            return requestTtl;
        }
        long defaultTtl = cacheProperties.getTtl().getDefaultSeconds();
        if (defaultTtl > 0) {
            return defaultTtl;
        }
        return -1L;
    }

    private double round1dp(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
