package com.cache.cluster.controller;

import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.service.ClusterRegistryService;
import com.cache.dto.request.NodeRegistrationRequest;
import com.cache.dto.response.ApiResponse;
import com.cache.dto.response.ClusterStatusResponse;
import com.cache.dto.response.NodeInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cluster")
@Validated
@Tag(name = "Cluster Management", description = "Phase 3 - Cluster Node Registry APIs")
public class ClusterController {

    private static final Logger log = LoggerFactory.getLogger(ClusterController.class);

    private final ClusterRegistryService clusterRegistryService;
    private final com.cache.cluster.routing.KeyRoutingService keyRoutingService;

    public ClusterController(ClusterRegistryService clusterRegistryService,
                             com.cache.cluster.routing.KeyRoutingService keyRoutingService) {
        this.clusterRegistryService = clusterRegistryService;
        this.keyRoutingService = keyRoutingService;
    }

    @Operation(
            summary = "Register a cache node",
            description = "Registers a new node with the cluster. If a node with the same ID is " +
                    "already registered, it is replaced (idempotent re-registration)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Node registered successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid registration request")
    })
    @PostMapping("/nodes")
    public ResponseEntity<ApiResponse<NodeInfoResponse>> registerNode(
            @Valid @RequestBody NodeRegistrationRequest request) {

        log.info("REST POST /cluster/nodes — registering node id='{}'", request.nodeId());
        NodeInfoResponse response = clusterRegistryService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Node registered successfully", response));
    }

    @Operation(
            summary = "List all cluster nodes",
            description = "Returns all registered nodes. Optionally filter by status."
    )
    @GetMapping("/nodes")
    public ResponseEntity<ApiResponse<List<NodeInfoResponse>>> getAllNodes(
            @Parameter(description = "Optional status filter: UP, DOWN, STARTING, SUSPECT")
            @RequestParam(required = false) NodeStatus status) {

        log.debug("REST GET /cluster/nodes status={}", status);
        List<NodeInfoResponse> nodes = status != null
                ? clusterRegistryService.getNodesByStatus(status)
                : clusterRegistryService.getAllNodes();

        return ResponseEntity.ok(ApiResponse.success(
                String.format("Retrieved %d node(s)", nodes.size()), nodes));
    }

    @Operation(
            summary = "Get a specific node by ID",
            description = "Returns the metadata for a single registered node."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Node found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Node not found")
    })
    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<ApiResponse<NodeInfoResponse>> getNode(
            @Parameter(description = "The node ID to look up", example = "node-1")
            @PathVariable
            @NotBlank String nodeId) {

        log.debug("REST GET /cluster/nodes/{}", nodeId);
        NodeInfoResponse response = clusterRegistryService.getNode(nodeId);
        return ResponseEntity.ok(ApiResponse.success("Node retrieved successfully", response));
    }

    @Operation(
            summary = "Deregister a node from the cluster",
            description = "Removes the node from the cluster registry."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Node deregistered"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Node not found")
    })
    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<ApiResponse<Void>> deregisterNode(
            @Parameter(description = "The node ID to deregister", example = "node-1")
            @PathVariable
            @NotBlank String nodeId) {

        log.info("REST DELETE /cluster/nodes/{}", nodeId);
        clusterRegistryService.deregister(nodeId);
        return ResponseEntity.ok(ApiResponse.success(
                "Node '" + nodeId + "' deregistered from cluster"));
    }

    @Operation(
            summary = "Mark a node as UP",
            description = "Transitions a node from STARTING to UP, signalling it is ready " +
                    "to serve cache traffic. Called by the node itself after startup completes."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Node marked UP"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Node not found")
    })
    @PatchMapping("/nodes/{nodeId}/up")
    public ResponseEntity<ApiResponse<NodeInfoResponse>> markNodeUp(
            @Parameter(description = "The node ID to mark as UP", example = "node-1")
            @PathVariable
            @NotBlank String nodeId) {

        log.info("REST PATCH /cluster/nodes/{}/up", nodeId);
        clusterRegistryService.markNodeUp(nodeId);
        NodeInfoResponse response = clusterRegistryService.getNode(nodeId);
        return ResponseEntity.ok(ApiResponse.success("Node '" + nodeId + "' is now UP", response));
    }

    @Operation(
            summary = "Record a heartbeat for a node (Phase 7)",
            description = "Updates the node's lastHeartbeatAt timestamp. If the node was SUSPECT, " +
                    "it transitions back to UP (self-healing recovery). " +
                    "Called by peer nodes after receiving a successful /ping response."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Heartbeat recorded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Node not found")
    })
    @PatchMapping("/nodes/{nodeId}/heartbeat")
    public ResponseEntity<ApiResponse<NodeInfoResponse>> recordHeartbeat(
            @Parameter(description = "The node ID whose heartbeat was confirmed", example = "node-2")
            @PathVariable
            @NotBlank String nodeId) {

        log.debug("REST PATCH /cluster/nodes/{}/heartbeat", nodeId);
        clusterRegistryService.recordHeartbeat(nodeId);
        NodeInfoResponse response = clusterRegistryService.getNode(nodeId);
        return ResponseEntity.ok(ApiResponse.success(
                "Heartbeat recorded for node '" + nodeId + "'", response));
    }

    @Operation(
            summary = "Get cluster health summary",
            description = "Returns aggregate counts and the full node list. Used by the dashboard."
    )
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ClusterStatusResponse>> getClusterStatus() {
        log.debug("REST GET /cluster/status");
        ClusterStatusResponse status = clusterRegistryService.getClusterStatus();
        return ResponseEntity.ok(ApiResponse.success("Cluster status retrieved", status));
    }

    @Operation(
            summary = "Get consistent hash ring mapping",
            description = "Returns the active mapping of virtual node hash positions to physical node IDs."
    )
    @GetMapping("/ring")
    public ResponseEntity<ApiResponse<List<com.cache.dto.response.VirtualNodeResponse>>> getRingMapping() {
        log.debug("REST GET /cluster/ring");
        List<com.cache.dto.response.VirtualNodeResponse> ring = clusterRegistryService.getRingMapping();
        return ResponseEntity.ok(ApiResponse.success("Consistent hash ring retrieved successfully", ring));
    }

    @Operation(
            summary = "Simulate node failure/recovery (manually update status)",
            description = "Allows manual state transition (UP, DOWN, SUSPECT) to test routing failover and ring rebuilds."
    )
    @PutMapping("/nodes/{nodeId}/status")
    public ResponseEntity<ApiResponse<NodeInfoResponse>> updateNodeStatus(
            @Parameter(description = "Node ID to update", example = "node-2")
            @PathVariable @NotBlank String nodeId,
            @Parameter(description = "Target status (UP, DOWN, SUSPECT)")
            @RequestParam @NotBlank String status) {

        log.info("REST PUT /cluster/nodes/{}/status -> {}", nodeId, status);
        NodeStatus nodeStatus = NodeStatus.valueOf(status.toUpperCase());
        NodeInfoResponse response = clusterRegistryService.updateNodeStatus(nodeId, nodeStatus);
        return ResponseEntity.ok(ApiResponse.success("Node status updated manually successfully", response));
    }

    @Operation(
            summary = "Resolve key hash and owner node",
            description = "Returns the hash position (64-bit long and hex) and target owner node for a key."
    )
    @GetMapping("/hash-key")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> resolveKeyHash(
            @RequestParam @NotBlank String key) {
        log.debug("REST GET /cluster/hash-key key='{}'", key);
        long hashVal = com.cache.cluster.routing.ConsistentHashRing.hash(key);
        String ownerNodeId = keyRoutingService.getOwnerNodeId(key);
        java.util.List<String> replicas = keyRoutingService.getReplicaNodeIds(key);

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("key", key);
        response.put("hash", hashVal);
        response.put("hashHex", String.format("%016x", hashVal));
        response.put("ownerNodeId", ownerNodeId);
        response.put("replicas", replicas);

        return ResponseEntity.ok(ApiResponse.success("Key routing resolved", response));
    }
}
