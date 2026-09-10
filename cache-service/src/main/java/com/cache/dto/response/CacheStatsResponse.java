package com.cache.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Snapshot of the current cache node statistics")
public record CacheStatsResponse(

        @Schema(description = "Node identifier", example = "node-1")
        String nodeId,

        @Schema(description = "Total number of live keys currently in the store")
        long totalKeys,

        @Schema(description = "Maximum number of keys this node is configured to hold")
        int maxCapacity,

        @Schema(description = "Percentage of capacity used", example = "42.5")
        double usagePercent,

        @Schema(description = "Total successful GET operations since startup")
        long totalHits,

        @Schema(description = "Total GET misses (key not found or expired) since startup")
        long totalMisses,

        @Schema(description = "Cache hit ratio as a percentage", example = "87.3")
        double hitRatio,

        @Schema(description = "Total entries evicted due to capacity limits since startup")
        long totalEvictions,

        @Schema(description = "Total entries removed due to TTL expiry since startup")
        long totalExpired,

        @Schema(description = "Active eviction policy name", example = "LRU")
        String evictionPolicy,

        @Schema(description = "Write replication statistics")
        com.cache.cluster.replication.ReplicationStats replicationStats

) {}
