package com.cache.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Overall cluster status snapshot including all registered nodes")
public record ClusterStatusResponse(

        @Schema(description = "Total number of nodes registered in the cluster")
        int totalNodes,

        @Schema(description = "Number of nodes currently in UP status")
        int upCount,

        @Schema(description = "Number of nodes in STARTING status")
        int startingCount,

        @Schema(description = "Number of nodes in SUSPECT status")
        int suspectCount,

        @Schema(description = "Number of nodes in DOWN status")
        int downCount,

        @Schema(description = "Whether the cluster is considered healthy (all nodes UP)")
        boolean clusterHealthy,

        @Schema(description = "Full list of all registered nodes with their metadata")
        List<NodeInfoResponse> nodes

) {}
