package com.cache.dto.response;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Metadata for a single cache node in the cluster")
public record NodeInfoResponse(

        @Schema(description = "Unique node identifier", example = "node-1")
        String nodeId,

        @Schema(description = "Hostname or IP of the node", example = "cache-node-1.internal")
        String host,

        @Schema(description = "Port the node listens on", example = "8081")
        int port,

        @Schema(description = "Current lifecycle status of the node", example = "UP")
        NodeStatus status,

        @Schema(description = "Full base URL for communicating with this node",
                example = "http://cache-node-1.internal:8081")
        String baseUrl,

        @Schema(description = "UTC timestamp when this node registered with the cluster")
        Instant registeredAt,

        @Schema(description = "UTC timestamp of the last heartbeat received from this node")
        Instant lastHeartbeatAt,

        @Schema(description = "Whether this node is currently available to serve cache traffic")
        boolean available

) {

    public static NodeInfoResponse from(NodeInfo node) {
        return new NodeInfoResponse(
                node.getId(),
                node.getHost(),
                node.getPort(),
                node.getStatus(),
                node.getBaseUrl(),
                node.getRegisteredAt(),
                node.getLastHeartbeatAt(),
                node.isAvailable()
        );
    }
}
