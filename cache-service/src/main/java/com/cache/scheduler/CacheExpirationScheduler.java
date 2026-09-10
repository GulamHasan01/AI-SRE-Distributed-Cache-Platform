package com.cache.scheduler;

import com.cache.store.CacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CacheExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CacheExpirationScheduler.class);

    private final CacheStore cacheStore;

    public CacheExpirationScheduler(CacheStore cacheStore) {
        this.cacheStore = cacheStore;
    }

    @Scheduled(fixedDelayString = "${cache.ttl.sweep-interval-ms:5000}")
    public void sweepExpiredEntries() {
        log.debug("TTL sweep starting — store size: {}", cacheStore.size());

        int removed = cacheStore.removeExpired();

        if (removed > 0) {
            log.info("TTL sweep complete: removed {} expired entries. Store size now: {}",
                    removed, cacheStore.size());
        } else {
            log.debug("TTL sweep complete: no expired entries found");
        }
    }
}
