package com.cache.cluster.health;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.config.CacheProperties;
import com.cache.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cluster/health")
@Tag(name = "Node Health", description = "Phase 7 - Self-Healing: liveness and failure-detector status")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final ClusterRegistry clusterRegistry;
    private final CacheProperties cacheProperties;

    public HealthController(ClusterRegistry clusterRegistry, CacheProperties cacheProperties) {
        this.clusterRegistry = clusterRegistry;
        this.cacheProperties = cacheProperties;
    }

    @Operation(
            summary = "Liveness ping",
            description = "Returns 200 immediately. Called by peer nodes during heartbeat rounds " +
                    "to confirm this node is reachable. No auth required — peer nodes must " +
                    "be able to reach this without credentials."
    )
    @GetMapping("/ping")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ping() {
        String nodeId = cacheProperties.getNode().getId();
        log.trace("PING received — node='{}' is alive", nodeId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("status", "UP");
        payload.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(ApiResponse.success("Pong", payload));
    }

    @Operation(
            summary = "Failure detector status",
            description = "Shows the current health view of every registered node: " +
                    "its status, last heartbeat time, heartbeat age, and how far it is " +
                    "from SUSPECT/DOWN thresholds. Used by the monitoring dashboard."
    )
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFailureDetectorStatus() {
        String selfId = cacheProperties.getNode().getId();
        long suspectThresholdMs = cacheProperties.getCluster().getSuspectThresholdMs();
        long downThresholdMs    = cacheProperties.getCluster().getDownThresholdMs();

        Collection<NodeInfo> nodes = clusterRegistry.findAll();
        Map<String, Object> nodeViews = new LinkedHashMap<>();

        for (NodeInfo node : nodes) {
            long ageMs = Duration.between(node.getLastHeartbeatAt(), Instant.now()).toMillis();
            boolean isSelf = selfId.equals(node.getId());

            Map<String, Object> view = new LinkedHashMap<>();
            view.put("nodeId", node.getId());
            view.put("host", node.getHost());
            view.put("port", node.getPort());
            view.put("status", node.getStatus());
            view.put("isSelf", isSelf);
            view.put("lastHeartbeatAt", node.getLastHeartbeatAt().toString());
            view.put("heartbeatAgeMs", ageMs);

            if (isSelf || node.getStatus() == NodeStatus.DOWN) {
                view.put("msUntilSuspect", "N/A");
                view.put("msUntilDown", "N/A");
            } else {
                view.put("msUntilSuspect", Math.max(0, suspectThresholdMs - ageMs));
                view.put("msUntilDown", Math.max(0, downThresholdMs - ageMs));
            }

            nodeViews.put(node.getId(), view);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("suspectThresholdMs", suspectThresholdMs);
        response.put("downThresholdMs", downThresholdMs);
        response.put("heartbeatIntervalMs", cacheProperties.getCluster().getHeartbeatIntervalMs());
        response.put("totalNodes", nodes.size());
        response.put("upCount", nodes.stream().filter(n -> n.getStatus() == NodeStatus.UP).count());
        response.put("suspectCount", nodes.stream().filter(n -> n.getStatus() == NodeStatus.SUSPECT).count());
        response.put("downCount", nodes.stream().filter(n -> n.getStatus() == NodeStatus.DOWN).count());
        response.put("nodes", nodeViews);

        return ResponseEntity.ok(ApiResponse.success("Failure detector status", response));
    }
}
