package com.cache.store;

import com.cache.config.CacheProperties;
import com.cache.eviction.EvictionPolicy;
import com.cache.exception.CacheCapacityExceededException;
import com.cache.model.CacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;

import com.cache.persistence.PersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class InMemoryCacheStore implements CacheStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryCacheStore.class);

    private final ConcurrentHashMap<String, CacheEntry> store;
    private final CacheProperties cacheProperties;
    private volatile EvictionPolicy evictionPolicy;
    private final PersistenceService persistenceService;

    private final AtomicLong evictionCount = new AtomicLong(0);
    private final AtomicLong expiredCount = new AtomicLong(0);

    private final Object evictionLock = new Object();

    private final Counter hitCounter;
    private final Counter missCounter;
    private final Counter evictionCounter;
    private final Counter expiryCounter;

    public InMemoryCacheStore(CacheProperties cacheProperties, EvictionPolicy evictionPolicy, PersistenceService persistenceService, MeterRegistry meterRegistry) {
        this.cacheProperties = cacheProperties;
        this.evictionPolicy = evictionPolicy;
        this.persistenceService = persistenceService;
        int initialCapacity = (int) (cacheProperties.getMaxSize() / 0.75) + 1;
        this.store = new ConcurrentHashMap<>(initialCapacity);

        String nodeId = cacheProperties.getNode().getId();
        this.hitCounter = meterRegistry.counter("cache.hits_total", "node", nodeId);
        this.missCounter = meterRegistry.counter("cache.misses_total", "node", nodeId);
        this.evictionCounter = meterRegistry.counter("cache.evictions_total", "node", nodeId);
        this.expiryCounter = meterRegistry.counter("cache.expiries_total", "node", nodeId);

        meterRegistry.gauge("cache.size", List.of(Tag.of("node", nodeId)), this.store, ConcurrentHashMap::size);

        log.info("InMemoryCacheStore initialized: maxSize={}, evictionPolicy={}",
                cacheProperties.getMaxSize(), evictionPolicy.policyName());
    }

    @PostConstruct
    public void init() {
        List<CacheEntry> recovered = persistenceService.loadSnapshot();
        for (CacheEntry entry : recovered) {
            store.put(entry.getKey(), entry);
        }
        if (!recovered.isEmpty()) {
            log.info("COLD START RECOVERY: Restored {} entries from snapshot disk persistence", recovered.size());
        }
    }

    @PreDestroy
    public void shutdownFlush() {
        log.info("SHUTDOWN FLUSH: Persisting snapshot before node shutdown...");
        int saved = persistenceService.saveSnapshot(store.values());
        log.info("Shutdown flush completed: {} entries saved to disk", saved);
    }

    @Override
    public void put(CacheEntry entry) {
        boolean isNewKey = !store.containsKey(entry.getKey());

        if (isNewKey && store.size() >= cacheProperties.getMaxSize()) {
            synchronized (evictionLock) {
                if (store.size() >= cacheProperties.getMaxSize()) {
                    Optional<String> victim = evictionPolicy.selectVictim(store.values());

                    if (victim.isPresent()) {
                        store.remove(victim.get());
                        evictionCount.incrementAndGet();
                        evictionCounter.increment();
                        log.debug("EVICT ({}): removed key='{}' to make room for key='{}'",
                                evictionPolicy.policyName(), victim.get(), entry.getKey());
                    } else {
                        log.warn("PUT rejected: cache full (size={}, max={}) and eviction policy is {}",
                                store.size(), cacheProperties.getMaxSize(), evictionPolicy.policyName());
                        throw new CacheCapacityExceededException(cacheProperties.getMaxSize(), store.size());
                    }
                }
            }
        }

        store.put(entry.getKey(), entry);
        log.debug("PUT key='{}' ttl={}s", entry.getKey(), entry.getTtlSeconds());
    }

    @Override
    public Optional<CacheEntry> get(String key) {
        CacheEntry entry = store.get(key);

        if (entry == null) {
            missCounter.increment();
            log.debug("GET key='{}' → MISS (not found)", key);
            return Optional.empty();
        }

        if (entry.isExpired()) {
            store.remove(key);
            expiredCount.incrementAndGet();
            expiryCounter.increment();
            missCounter.increment();
            log.debug("GET key='{}' → EXPIRED (lazy removal)", key);
            return Optional.empty();
        }

        entry.recordAccess();
        hitCounter.increment();
        log.debug("GET key='{}' → HIT (accessCount={})", key, entry.getAccessCount());
        return Optional.of(entry);
    }

    @Override
    public boolean delete(String key) {
        boolean removed = store.remove(key) != null;
        log.debug("DELETE key='{}' → {}", key, removed ? "removed" : "not found");
        return removed;
    }

    @Override
    public long clear() {
        long count = store.size();
        store.clear();
        log.info("CLEAR: removed {} entries", count);
        return count;
    }

    @Override
    public int removeExpired() {
        int removed = 0;
        for (CacheEntry entry : store.values()) {
            if (entry.isExpired()) {

                store.remove(entry.getKey());
                expiredCount.incrementAndGet();
                expiryCounter.increment();
                removed++;
                log.debug("SWEEP: expired key='{}' removed", entry.getKey());
            }
        }
        if (removed > 0) {
            log.info("TTL sweep: removed {} expired entries", removed);
        }
        return removed;
    }

    @Override
    public long size() {
        return store.size();
    }

    @Override
    public boolean isEmpty() {
        return store.isEmpty();
    }

    @Override
    public boolean containsKey(String key) {
        return store.containsKey(key);
    }

    @Override
    public Collection<CacheEntry> getAllEntries() {
        return Collections.unmodifiableCollection(store.values());
    }

    @Override
    public long getEvictionCount() {
        return evictionCount.get();
    }

    @Override
    public long getExpiredCount() {
        return expiredCount.get();
    }

    @Override
    public void setEvictionPolicy(EvictionPolicy policy) {
        this.evictionPolicy = policy;
    }

    @Override
    public EvictionPolicy getEvictionPolicy() {
        return this.evictionPolicy;
    }
}
