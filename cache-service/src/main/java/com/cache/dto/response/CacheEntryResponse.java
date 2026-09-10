package com.cache.dto.response;

import com.cache.model.CacheEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Represents a single cache entry returned from the store")
public record CacheEntryResponse(

        @Schema(description = "The cache key", example = "user:1001:profile")
        String key,

        @Schema(description = "The stored value", example = "{\"name\":\"Alice\"}")
        String value,

        @Schema(description = "UTC timestamp when this entry was created")
        Instant createdAt,

        @Schema(description = "Number of times this entry has been read since creation")
        long accessCount,

        @Schema(description = "Configured TTL in seconds; -1 means no expiry", example = "300")
        long ttlSeconds,

        @Schema(description = "Seconds remaining before this entry expires; -1 if no TTL", example = "247")
        long remainingTtlSeconds,

        @Schema(description = "Whether this entry has already expired (should be false — expired entries are auto-removed)")
        boolean expired

) {

    public static CacheEntryResponse from(CacheEntry entry) {
        return new CacheEntryResponse(
                entry.getKey(),
                entry.getValue(),
                entry.getCreatedAt(),
                entry.getAccessCount(),
                entry.getTtlSeconds(),
                entry.getRemainingTtlSeconds(),
                entry.isExpired()
        );
    }
}
