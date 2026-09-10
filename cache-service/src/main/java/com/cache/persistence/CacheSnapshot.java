package com.cache.persistence;

import java.time.Instant;
import java.util.List;

public record CacheSnapshot(
        String nodeNodeId,
        Instant timestamp,
        List<SnapshotEntry> entries
) {
    public record SnapshotEntry(
            String key,
            String value,
            Instant createdAt,
            long ttlSeconds
    ) {}
}
