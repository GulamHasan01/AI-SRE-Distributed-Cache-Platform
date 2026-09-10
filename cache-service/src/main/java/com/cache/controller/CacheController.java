package com.cache.controller;

import com.cache.cluster.forwarding.RequestForwardingService;
import com.cache.cluster.routing.KeyRoutingService;
import com.cache.config.CacheProperties;
import com.cache.dto.request.CachePutRequest;
import com.cache.dto.response.ApiResponse;
import com.cache.dto.response.CacheEntryResponse;
import com.cache.dto.response.CacheStatsResponse;
import com.cache.service.CacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cache")
@Validated
@Tag(name = "Cache Operations", description = "Phase 6 - Distributed Cache API with Data Replication")
public class CacheController {

    private static final Logger log = LoggerFactory.getLogger(CacheController.class);

    public static final String TARGET_NODE_HEADER = "X-Target-Node";

    public static final String FORWARDED_BY_HEADER = "X-Forwarded-By";

    public static final String REPLICATED_FROM_HEADER = "X-Replicated-From";

    private final CacheService cacheService;
    private final RequestForwardingService forwardingService;
    private final KeyRoutingService keyRoutingService;
    private final CacheProperties cacheProperties;
    private final com.cache.cluster.replication.ReplicationService replicationService;

    public CacheController(CacheService cacheService,
                           RequestForwardingService forwardingService,
                           KeyRoutingService keyRoutingService,
                           CacheProperties cacheProperties,
                           com.cache.cluster.replication.ReplicationService replicationService) {
        this.cacheService = cacheService;
        this.forwardingService = forwardingService;
        this.keyRoutingService = keyRoutingService;
        this.cacheProperties = cacheProperties;
        this.replicationService = replicationService;
    }

    @Operation(
            summary = "Store a key-value pair",
            description = "Creates or updates a cache entry. Automatically routes to the responsible node and replicates to backup nodes."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Entry stored successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Target node unreachable"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "507", description = "Cache is full")
    })
    @PutMapping
    public ResponseEntity<ApiResponse<CacheEntryResponse>> put(
            @Parameter(description = "Loop-prevention sentinel. Set by forwarding nodes; triggers local handling.")
            @RequestHeader(value = FORWARDED_BY_HEADER, required = false) String forwardedBy,
            @Parameter(description = "Optional explicit target node ID. Overrides consistent hash routing when present.")
            @RequestHeader(value = TARGET_NODE_HEADER, required = false) String targetNodeId,
            @Parameter(description = "Replication source node ID. Indicates this request is a backup replication copy.")
            @RequestHeader(value = REPLICATED_FROM_HEADER, required = false) String replicatedFrom,
            @Valid @RequestBody CachePutRequest request) {

        if (replicatedFrom != null && !replicatedFrom.isBlank()) {
            log.info("PUT REPLICATED key='{}' (from '{}')", request.key(), replicatedFrom);
            CacheEntryResponse response = cacheService.put(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Entry replicated locally successfully", response));
        }

        String owner = resolveOwner(forwardedBy, targetNodeId, request.key());
        if (!owner.equals(getSelfNodeId())) {
            log.info("PUT FORWARD key='{}' -> node='{}'", request.key(), owner);
            CacheEntryResponse forwarded = forwardingService.forwardPut(owner, request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Entry forwarded and stored on node '" + owner + "' successfully", forwarded));
        }

        log.info("PUT LOCAL key='{}'", request.key());
        CacheEntryResponse response = cacheService.put(request);

        java.util.List<String> replicas = keyRoutingService.getReplicaNodeIds(request.key());
        replicationService.replicatePutAsync(request, replicas, getSelfNodeId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Entry stored and replicated successfully", response));
    }

    @Operation(
            summary = "Get a cache entry by key",
            description = "Retrieves the value for the given key. Supports failover reads from backup replicas if primary node is down."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Entry found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Key not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Target node unreachable")
    })
    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<CacheEntryResponse>> get(
            @Parameter(description = "Loop-prevention sentinel. Set by forwarding nodes; triggers local handling.")
            @RequestHeader(value = FORWARDED_BY_HEADER, required = false) String forwardedBy,
            @Parameter(description = "Optional explicit target node ID. Overrides consistent hash routing when present.")
            @RequestHeader(value = TARGET_NODE_HEADER, required = false) String targetNodeId,
            @Parameter(description = "Replication source node ID. Indicates this request is a backup replication copy.")
            @RequestHeader(value = REPLICATED_FROM_HEADER, required = false) String replicatedFrom,
            @Parameter(description = "Cache key to retrieve", example = "user:1001:profile")
            @PathVariable
            @NotBlank(message = "Key must not be blank")
            @Size(max = 256, message = "Key must not exceed 256 characters")
            String key) {

        if (replicatedFrom != null && !replicatedFrom.isBlank()) {
            log.info("GET REPLICATED key='{}' (from '{}')", key, replicatedFrom);
            CacheEntryResponse response = cacheService.get(key);
            return ResponseEntity.ok(ApiResponse.success("Entry retrieved locally successfully", response));
        }

        String owner = resolveOwner(forwardedBy, targetNodeId, key);
        if (!owner.equals(getSelfNodeId())) {
            log.info("GET FORWARD key='{}' -> node='{}'", key, owner);
            try {
                CacheEntryResponse forwarded = forwardingService.forwardGet(owner, key);
                return ResponseEntity.ok(ApiResponse.success(
                        "Entry retrieved from node '" + owner + "' successfully", forwarded));
            } catch (com.cache.cluster.exception.NodeCommunicationException e) {
                log.warn("Owner node '{}' was unreachable on GET key='{}', trying failover to replicas...", owner, key);

                java.util.List<String> replicas = keyRoutingService.getReplicaNodeIds(key);
                if (replicas != null && !replicas.isEmpty()) {
                    for (String replicaId : replicas) {
                        try {
                            if (replicaId.equals(getSelfNodeId())) {
                                log.info("GET FAILOVER local fallback key='{}'", key);
                                CacheEntryResponse response = cacheService.get(key);
                                return ResponseEntity.ok(ApiResponse.success(
                                        "Entry retrieved from local backup replica successfully", response));
                            } else {
                                log.info("GET FAILOVER key='{}' -> replica node='{}'", key, replicaId);
                                CacheEntryResponse response = forwardingService.forwardGet(replicaId, key);
                                return ResponseEntity.ok(ApiResponse.success(
                                        "Entry retrieved from backup replica node '" + replicaId + "' successfully", response));
                            }
                        } catch (Exception ex) {
                            log.warn("Failover replica node '{}' was also unreachable/failed on GET: {}",
                                    replicaId, ex.getMessage());
                        }
                    }
                }

                throw e;
            }
        }

        log.info("GET LOCAL key='{}'", key);
        try {
            CacheEntryResponse response = cacheService.get(key);
            return ResponseEntity.ok(ApiResponse.success("Entry retrieved successfully", response));
        } catch (com.cache.exception.CacheKeyNotFoundException e) {
            if (replicatedFrom != null && !replicatedFrom.isBlank()) {
                throw e;
            }
            log.warn("GET LOCAL missed key='{}' on coordinator. Trying read fallback from replicas...", key);
            java.util.List<String> replicas = keyRoutingService.getReplicaNodeIds(key);
            if (replicas != null && !replicas.isEmpty()) {
                for (String replicaId : replicas) {
                    if (replicaId.equals(getSelfNodeId())) {
                        continue;
                    }
                    try {
                        log.info("GET FALLBACK key='{}' -> replica node='{}'", key, replicaId);
                        CacheEntryResponse response = forwardingService.forwardGet(replicaId, key);
                        log.info("GET FALLBACK key='{}' found on replica '{}'. Performing write repair...", key, replicaId);
                        long remainingTtl = response.remainingTtlSeconds();
                        Long repairTtl = (remainingTtl > 0) ? remainingTtl : null;
                        cacheService.put(new CachePutRequest(key, response.value(), repairTtl));
                        return ResponseEntity.ok(ApiResponse.success(
                                "Entry retrieved from backup replica node '" + replicaId + "' and repaired locally successfully", response));
                    } catch (Exception ex) {
                        log.warn("Read fallback replica node '{}' failed or key not found: {}",
                                replicaId, ex.getMessage());
                    }
                }
            }
            throw e;
        }
    }

    @Operation(
            summary = "Delete a cache entry by key",
            description = "Removes the entry for the given key. Replicates deletion to backup replica nodes."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Entry deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Key not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Target node unreachable")
    })
    @DeleteMapping("/{key}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Loop-prevention sentinel. Set by forwarding nodes; triggers local handling.")
            @RequestHeader(value = FORWARDED_BY_HEADER, required = false) String forwardedBy,
            @Parameter(description = "Optional explicit target node ID. Overrides consistent hash routing when present.")
            @RequestHeader(value = TARGET_NODE_HEADER, required = false) String targetNodeId,
            @Parameter(description = "Replication source node ID. Indicates this request is a backup replication copy.")
            @RequestHeader(value = REPLICATED_FROM_HEADER, required = false) String replicatedFrom,
            @Parameter(description = "Cache key to delete", example = "user:1001:profile")
            @PathVariable
            @NotBlank(message = "Key must not be blank")
            String key) {

        if (replicatedFrom != null && !replicatedFrom.isBlank()) {
            log.info("DELETE REPLICATED key='{}' (from '{}')", key, replicatedFrom);
            cacheService.delete(key);
            return ResponseEntity.ok(ApiResponse.success("Entry deletion replicated locally successfully"));
        }

        String owner = resolveOwner(forwardedBy, targetNodeId, key);
        if (!owner.equals(getSelfNodeId())) {
            log.info("DELETE FORWARD key='{}' -> node='{}'", key, owner);
            forwardingService.forwardDelete(owner, key);
            return ResponseEntity.ok(ApiResponse.success("Entry deleted from node '" + owner + "' successfully"));
        }

        log.info("DELETE LOCAL key='{}'", key);
        cacheService.delete(key);

        java.util.List<String> replicas = keyRoutingService.getReplicaNodeIds(key);
        replicationService.replicateDeleteAsync(key, replicas, getSelfNodeId());

        return ResponseEntity.ok(ApiResponse.success("Entry deleted and replication cleared successfully"));
    }

    @Operation(
            summary = "Clear all cache entries",
            description = "Removes ALL entries from this node's local cache. Use with caution in production."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cache cleared successfully")
    })
    @DeleteMapping
    public ResponseEntity<ApiResponse<Long>> clear() {
        log.info("REST CLEAR all cache entries");
        long removed = cacheService.clear();
        return ResponseEntity.ok(ApiResponse.success(
                String.format("Cache cleared. %d entries removed.", removed), removed));
    }

    @Operation(
            summary = "Get cache node statistics",
            description = "Returns operational metrics for this node: total keys, capacity usage, hit/miss ratios."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stats retrieved successfully")
    })
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<CacheStatsResponse>> getStats() {
        log.debug("REST GET cache stats");
        CacheStatsResponse stats = cacheService.getStats();
        return ResponseEntity.ok(ApiResponse.success("Stats retrieved successfully", stats));
    }

    @Operation(
            summary = "Update cache eviction policy dynamically",
            description = "Switches the active eviction strategy (LRU, LFU, FIFO, NO_EVICTION) at runtime."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Eviction policy updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid policy name supplied")
    })
    @PutMapping("/config/eviction-policy")
    public ResponseEntity<ApiResponse<String>> updateEvictionPolicy(@RequestParam @NotBlank String policy) {
        log.info("REST PUT update eviction policy to '{}'", policy);
        try {
            cacheService.updateEvictionPolicy(policy);
            return ResponseEntity.ok(ApiResponse.success("Eviction policy updated successfully", policy.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("Invalid eviction policy name", e.getMessage()));
        }
    }

    private String resolveOwner(String forwardedBy, String targetNodeId, String key) {
        if (forwardedBy != null && !forwardedBy.isBlank()) {
            log.debug("Loop guard: request for key='{}' already forwarded by '{}', handling locally",
                    key, forwardedBy);
            return getSelfNodeId();
        }
        if (targetNodeId != null && !targetNodeId.isBlank()) {
            return targetNodeId;
        }
        return keyRoutingService.getOwnerNodeId(key);
    }

    private String getSelfNodeId() {
        return cacheProperties.getNode().getId();
    }
}