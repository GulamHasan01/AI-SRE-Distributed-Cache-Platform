package com.cache.cluster.health;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.config.CacheProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom Spring Boot Actuator HealthIndicator reporting cluster-wide status,
 * quorum availability, and hash ring stability.
 */
@Component("cluster")
public class ClusterHealthIndicator implements HealthIndicator {

    private final ClusterRegistry clusterRegistry;
    private final CacheProperties cacheProperties;

    public ClusterHealthIndicator(ClusterRegistry clusterRegistry, CacheProperties cacheProperties) {
        this.clusterRegistry = clusterRegistry;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public Health health() {
        Collection<NodeInfo> allNodes = clusterRegistry.getAllNodes();
        String selfNodeId = cacheProperties.getNode().getId();

        long healthyCount = allNodes.stream()
                .filter(n -> n.getStatus() == NodeStatus.HEALTHY)
                .count();

        int totalCount = allNodes.size();
        int quorumRequired = totalCount > 0 ? (totalCount / 2) + 1 : 1;
        boolean hasQuorum = healthyCount >= quorumRequired;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("localNodeId", selfNodeId);
        details.put("totalNodes", totalCount);
        details.put("healthyNodes", healthyCount);
        details.put("quorumRequired", quorumRequired);
        details.put("quorumSatisfied", hasQuorum);

        Map<String, String> nodeStatuses = new LinkedHashMap<>();
        for (NodeInfo node : allNodes) {
            nodeStatuses.put(node.getId(), node.getStatus().name());
        }
        details.put("nodes", nodeStatuses);

        if (!hasQuorum) {
            return Health.down()
                    .withDetails(details)
                    .withDetail("reason", "Cluster quorum lost: healthy nodes (" + healthyCount + ") < quorum required (" + quorumRequired + ")")
                    .build();
        }

        if (healthyCount < totalCount) {
            return Health.status("DEGRADED")
                    .withDetails(details)
                    .withDetail("reason", "One or more nodes are unreachable or degraded")
                    .build();
        }

        return Health.up()
                .withDetails(details)
                .build();
    }
}
