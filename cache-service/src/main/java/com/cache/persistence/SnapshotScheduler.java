package com.cache.persistence;

import com.cache.config.CacheProperties;
import com.cache.store.CacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotScheduler.class);

    private final CacheStore cacheStore;
    private final PersistenceService persistenceService;
    private final CacheProperties cacheProperties;

    public SnapshotScheduler(CacheStore cacheStore, PersistenceService persistenceService, CacheProperties cacheProperties) {
        this.cacheStore = cacheStore;
        this.persistenceService = persistenceService;
        this.cacheProperties = cacheProperties;
    }

    @Scheduled(fixedDelayString = "${cache.persistence.snapshot-interval-seconds:60}000")
    public void scheduledSnapshot() {
        if (!cacheProperties.getPersistence().isEnabled()) {
            return;
        }

        log.debug("Starting scheduled cache snapshot...");
        int savedCount = persistenceService.saveSnapshot(cacheStore.getAllEntries());
        log.debug("Scheduled snapshot completed: {} entries saved", savedCount);
    }
}
