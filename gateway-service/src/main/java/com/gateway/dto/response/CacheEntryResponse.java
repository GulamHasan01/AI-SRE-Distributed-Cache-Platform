package com.gateway.dto.response;

import java.time.Instant;

public record CacheEntryResponse(
        String key,
        String value,
        Instant createdAt,
        long accessCount,
        long ttlSeconds,
        long remainingTtlSeconds,
        boolean expired
) {}
