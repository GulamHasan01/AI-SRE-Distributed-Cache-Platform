package com.gateway.controller;

import com.gateway.cluster.model.NodeInfo;
import com.gateway.cluster.model.NodeStatus;
import com.gateway.dto.request.CachePutRequest;
import com.gateway.dto.response.ApiResponse;
import com.gateway.dto.response.CacheEntryResponse;
import com.gateway.dto.response.VirtualNodeResponse;
import com.gateway.routing.ConsistentHashRing;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/v1/gateway")
@Validated
@EnableScheduling
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);
    private static final String TRACE_HEADER = "X-Trace-Id";

    @Value("${cluster.nodes}")
    private List<String> configuredNodes;

    private final List<String> activeConfiguredNodes = new CopyOnWriteArrayList<>();

    private final RestClient restClient = RestClient.create();

    private final List<NodeInfo> clusterNodes = new CopyOnWriteArrayList<>();

    private volatile ConsistentHashRing hashRing = new ConsistentHashRing(Collections.emptyList(), 150);

    @PostConstruct
    public void init() {
        if (configuredNodes != null) {
            activeConfiguredNodes.addAll(configuredNodes);
        }
        refreshClusterRing();
    }

    @Scheduled(fixedDelay = 3000)
    public void scheduledRingRefresh() {
        refreshClusterRing();
    }

    private synchronized void refreshClusterRing() {
        List<NodeInfo> fetchedNodes = fetchClusterStatusFromBackend();
        if (!fetchedNodes.isEmpty()) {
            this.clusterNodes.clear();
            this.clusterNodes.addAll(fetchedNodes);

            List<NodeInfo> upNodes = fetchedNodes.stream()
                    .filter(n -> n.getStatus() == NodeStatus.UP)
                    .toList();

            this.hashRing = new ConsistentHashRing(upNodes, 150);
            log.debug("Gateway Consistent Hash Ring rebuilt. Total nodes: {}, Active nodes: {}",
                    fetchedNodes.size(), upNodes.size());
        } else {
            log.warn("Could not fetch cluster topology from any backend nodes.");
        }
    }

    @SuppressWarnings("unchecked")
    private List<NodeInfo> fetchClusterStatusFromBackend() {
        for (String nodeUrl : activeConfiguredNodes) {
            try {
                ResponseEntity<ApiResponse<Map<String, Object>>> response = restClient.get()
                        .uri(nodeUrl + "/api/v1/cluster/status")
                        .retrieve()
                        .toEntity(new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> data = response.getBody().data();
                    if (data != null && data.get("nodes") != null) {
                        List<Map<String, Object>> rawNodes = (List<Map<String, Object>>) data.get("nodes");
                        List<NodeInfo> nodes = new ArrayList<>();
                        for (Map<String, Object> raw : rawNodes) {
                            NodeInfo node = new NodeInfo();
                            node.setId((String) raw.get("nodeId"));
                            node.setHost((String) raw.get("host"));
                            node.setPort((Integer) raw.get("port"));
                            node.setStatus(NodeStatus.valueOf((String) raw.get("status")));
                            nodes.add(node);
                        }
                        return nodes;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to connect to node {} to fetch status: {}", nodeUrl, e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    private String getTraceId(HttpServletRequest request, HttpServletResponse response) {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        response.setHeader(TRACE_HEADER, traceId);
        return traceId;
    }

    private NodeInfo getOwnerNode(String key) {
        String ownerId = hashRing.getNodeForKey(key)
                .orElseThrow(() -> new IllegalStateException("No active UP nodes in the consistent hash ring!"));

        return clusterNodes.stream()
                .filter(n -> n.getId().equals(ownerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Owner node ID " + ownerId + " not found in registered node list!"));
    }

    @GetMapping("/cache/{key}")
    public ResponseEntity<ApiResponse<CacheEntryResponse>> getCache(
            @PathVariable @NotBlank String key,
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request, response);
        NodeInfo owner = getOwnerNode(key);
        log.info("Gateway GET key='{}' -> routing to owner='{}' ({}) [traceId={}]",
                key, owner.getId(), owner.getBaseUrl(), traceId);

        try {
            ResponseEntity<ApiResponse<CacheEntryResponse>> res = restClient.get()
                    .uri(owner.getBaseUrl() + "/api/v1/cache/" + key)
                    .header(TRACE_HEADER, traceId)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<ApiResponse<CacheEntryResponse>>() {});

            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            log.error("Failed proxying GET key='{}' to node '{}': {}", key, owner.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.failure("Failed proxying GET operation", e.getMessage()));
        }
    }

    @PostMapping("/cache")
    public ResponseEntity<ApiResponse<CacheEntryResponse>> putCache(
            @Valid @RequestBody CachePutRequest payload,
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request, response);
        NodeInfo owner = getOwnerNode(payload.key());
        log.info("Gateway PUT key='{}' -> routing to owner='{}' ({}) [traceId={}]",
                payload.key(), owner.getId(), owner.getBaseUrl(), traceId);

        try {
            ResponseEntity<ApiResponse<CacheEntryResponse>> res = restClient.put()
                    .uri(owner.getBaseUrl() + "/api/v1/cache")
                    .header(TRACE_HEADER, traceId)
                    .body(payload)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<ApiResponse<CacheEntryResponse>>() {});

            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            log.error("Failed proxying PUT key='{}' to node '{}': {}", payload.key(), owner.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.failure("Failed proxying PUT operation", e.getMessage()));
        }
    }

    @DeleteMapping("/cache/{key}")
    public ResponseEntity<ApiResponse<Void>> deleteCache(
            @PathVariable @NotBlank String key,
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request, response);
        NodeInfo owner = getOwnerNode(key);
        log.info("Gateway DELETE key='{}' -> routing to owner='{}' ({}) [traceId={}]",
                key, owner.getId(), owner.getBaseUrl(), traceId);

        try {
            ResponseEntity<ApiResponse<Void>> res = restClient.delete()
                    .uri(owner.getBaseUrl() + "/api/v1/cache/" + key)
                    .header(TRACE_HEADER, traceId)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<ApiResponse<Void>>() {});

            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            log.error("Failed proxying DELETE key='{}' to node '{}': {}", key, owner.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.failure("Failed proxying DELETE operation", e.getMessage()));
        }
    }

    @GetMapping("/cluster/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getClusterStatus(
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request, response);
        for (String nodeUrl : configuredNodes) {
            try {
                ResponseEntity<ApiResponse<Map<String, Object>>> res = restClient.get()
                        .uri(nodeUrl + "/api/v1/cluster/status")
                        .header(TRACE_HEADER, traceId)
                        .retrieve()
                        .toEntity(new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});

                return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
            } catch (Exception e) {
            }
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure("No cluster nodes reachable", "All backend nodes disconnected"));
    }

    @GetMapping("/cluster/ring")
    public ResponseEntity<ApiResponse<List<VirtualNodeResponse>>> getRing(
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request, response);
        Map<Long, String> ringMap = hashRing.getRingMap();
        List<VirtualNodeResponse> ringList = new ArrayList<>();

        ringMap.forEach((pos, nodeId) -> {
            String hex = String.format("%016x", pos);
            ringList.add(new VirtualNodeResponse(hex, pos, nodeId));
        });

        ringList.sort(Comparator.comparingLong(VirtualNodeResponse::position));
        return ResponseEntity.ok(ApiResponse.success("Virtual nodes ring retrieved successfully", ringList));
    }

    @GetMapping("/cluster/hash-key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHashKey(
            @RequestParam @NotBlank String key,
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request, response);
        long hash = ConsistentHashRing.hash(key);
        String hashHex = String.format("%016x", hash);

        String ownerNodeId = hashRing.getNodeForKey(key).orElse("none");
        List<String> replicas = hashRing.getNodesForKey(key, 3);
        if (!replicas.isEmpty() && replicas.get(0).equals(ownerNodeId)) {
            replicas.remove(0);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("hash", hash);
        data.put("hashHex", hashHex);
        data.put("ownerNodeId", ownerNodeId);
        data.put("replicas", replicas);

        return ResponseEntity.ok(ApiResponse.success("Key routing resolved successfully", data));
    }

    @PutMapping("/cluster/nodes/{nodeId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateNodeStatus(
            @PathVariable @NotBlank String nodeId,
            @RequestParam @NotBlank String status,
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request, response);
        log.info("Simulating cluster status change: node '{}' -> status '{}' [traceId={}]", nodeId, status, traceId);

        boolean success = false;
        String errorMessage = null;
        ResponseEntity<ApiResponse<Map<String, Object>>> lastResponse = null;

        for (String nodeUrl : configuredNodes) {
            try {
                ResponseEntity<ApiResponse<Map<String, Object>>> res = restClient.put()
                        .uri(nodeUrl + "/api/v1/cluster/nodes/" + nodeId + "/status?status=" + status)
                        .header(TRACE_HEADER, traceId)
                        .retrieve()
                        .toEntity(new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});

                if (res.getStatusCode().is2xxSuccessful()) {
                    success = true;
                    lastResponse = res;
                    break;
                }
            } catch (Exception e) {
                errorMessage = e.getMessage();
            }
        }

        if (success) {
            refreshClusterRing();
            return ResponseEntity.ok(ApiResponse.success("Node status updated successfully and ring rebuilt", lastResponse.getBody().data()));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.failure("Failed to update status on backing cluster", errorMessage));
        }
    }

    @GetMapping("/cluster/health-report")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getClusterHealthReport(
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request, response);
        Map<String, Object> report = new LinkedHashMap<>();

        long totalNodes = clusterNodes.size();
        long upNodes = clusterNodes.stream().filter(n -> n.getStatus() == NodeStatus.UP).count();
        long virtualNodes = hashRing.getRingMap().size();

        report.put("totalNodes", totalNodes);
        report.put("activeUpNodes", upNodes);
        report.put("virtualNodesInRing", virtualNodes);
        report.put("configuredBackendUrls", activeConfiguredNodes);
        report.put("nodes", clusterNodes);
        report.put("generatedAt", java.time.Instant.now());

        return ResponseEntity.ok(ApiResponse.success("Cluster health report generated successfully", report));
    }

    @GetMapping("/cluster/metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getClusterMetrics(
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request, response);
        List<Map<String, Object>> nodeStatsList = new ArrayList<>();

        long clusterTotalKeys = 0;
        long clusterTotalHits = 0;
        long clusterTotalMisses = 0;
        long clusterTotalEvictions = 0;
        long clusterTotalExpired = 0;

        for (String nodeUrl : activeConfiguredNodes) {
            try {
                ResponseEntity<ApiResponse<Map<String, Object>>> res = restClient.get()
                        .uri(nodeUrl + "/api/v1/cache/stats")
                        .header(TRACE_HEADER, traceId)
                        .retrieve()
                        .toEntity(new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});

                if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null && res.getBody().data() != null) {
                    Map<String, Object> data = res.getBody().data();
                    nodeStatsList.add(data);

                    clusterTotalKeys += ((Number) data.getOrDefault("totalKeys", 0)).longValue();
                    clusterTotalHits += ((Number) data.getOrDefault("totalHits", 0)).longValue();
                    clusterTotalMisses += ((Number) data.getOrDefault("totalMisses", 0)).longValue();
                    clusterTotalEvictions += ((Number) data.getOrDefault("totalEvictions", 0)).longValue();
                    clusterTotalExpired += ((Number) data.getOrDefault("totalExpired", 0)).longValue();
                }
            } catch (Exception e) {
                log.debug("Could not fetch stats from node {}: {}", nodeUrl, e.getMessage());
            }
        }

        long totalOps = clusterTotalHits + clusterTotalMisses;
        double clusterHitRatio = totalOps > 0 ? (double) clusterTotalHits / totalOps * 100.0 : 0.0;

        Map<String, Object> aggregated = new LinkedHashMap<>();
        aggregated.put("clusterTotalKeys", clusterTotalKeys);
        aggregated.put("clusterTotalHits", clusterTotalHits);
        aggregated.put("clusterTotalMisses", clusterTotalMisses);
        aggregated.put("clusterHitRatio", Math.round(clusterHitRatio * 100.0) / 100.0);
        aggregated.put("clusterTotalEvictions", clusterTotalEvictions);
        aggregated.put("clusterTotalExpired", clusterTotalExpired);
        aggregated.put("reachableNodeCount", nodeStatsList.size());
        aggregated.put("perNodeStats", nodeStatsList);

        return ResponseEntity.ok(ApiResponse.success("Cluster metrics aggregated successfully", aggregated));
    }

    @PutMapping("/cluster/eviction-policy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateEvictionPolicy(
            @RequestParam @NotBlank String policy,
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request, response);
        log.info("Broadcasting eviction policy change to '{}' across cluster [traceId={}]", policy, traceId);

        List<String> successNodes = new ArrayList<>();
        List<String> failedNodes = new ArrayList<>();

        for (String nodeUrl : activeConfiguredNodes) {
            try {
                ResponseEntity<ApiResponse<String>> res = restClient.put()
                        .uri(nodeUrl + "/api/v1/cache/config/eviction-policy?policy=" + policy)
                        .header(TRACE_HEADER, traceId)
                        .retrieve()
                        .toEntity(new ParameterizedTypeReference<ApiResponse<String>>() {});

                if (res.getStatusCode().is2xxSuccessful()) {
                    successNodes.add(nodeUrl);
                } else {
                    failedNodes.add(nodeUrl);
                }
            } catch (Exception e) {
                failedNodes.add(nodeUrl + " (" + e.getMessage() + ")");
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("policy", policy.toUpperCase());
        data.put("successNodes", successNodes);
        data.put("failedNodes", failedNodes);

        if (successNodes.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ApiResponse<>(false, "Failed to switch eviction policy on all cluster nodes", java.time.Instant.now(), data, "All nodes failed"));
        }

        return ResponseEntity.ok(ApiResponse.success("Cluster eviction policy updated successfully", data));
    }

    @PostMapping("/cluster/config/nodes")
    public ResponseEntity<ApiResponse<List<String>>> addConfiguredNode(@RequestParam @NotBlank String url) {
        log.info("Adding node '{}' to dynamic cluster configuration", url);
        if (!activeConfiguredNodes.contains(url)) {
            activeConfiguredNodes.add(url);
            refreshClusterRing();
            return ResponseEntity.ok(ApiResponse.success("Node added to cluster layout successfully", activeConfiguredNodes));
        }
        return ResponseEntity.badRequest().body(ApiResponse.failure("Node URL already exists in cluster", activeConfiguredNodes.toString()));
    }

    @DeleteMapping("/cluster/config/nodes")
    public ResponseEntity<ApiResponse<List<String>>> removeConfiguredNode(@RequestParam @NotBlank String url) {
        log.info("Removing node '{}' from dynamic cluster configuration", url);
        if (activeConfiguredNodes.remove(url)) {
            clusterNodes.removeIf(n -> url.contains(String.valueOf(n.getPort())));
            refreshClusterRing();
            return ResponseEntity.ok(ApiResponse.success("Node removed from cluster layout successfully", activeConfiguredNodes));
        }
        return ResponseEntity.badRequest().body(ApiResponse.failure("Node URL not found in cluster configuration", activeConfiguredNodes.toString()));
    }
}
