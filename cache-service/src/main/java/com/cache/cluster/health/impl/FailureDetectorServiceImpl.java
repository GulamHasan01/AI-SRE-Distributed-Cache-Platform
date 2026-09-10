package com.cache.cluster.health.impl;

import com.cache.cluster.health.FailureDetectorService;
import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.cluster.routing.ClusterRingManager;
import com.cache.config.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

@Service
public class FailureDetectorServiceImpl implements FailureDetectorService {

    private static final Logger log = LoggerFactory.getLogger(FailureDetectorServiceImpl.class);

    private final ClusterRegistry clusterRegistry;
    private final ClusterRingManager clusterRingManager;
    private final CacheProperties cacheProperties;

    public FailureDetectorServiceImpl(ClusterRegistry clusterRegistry,
                                      ClusterRingManager clusterRingManager,
                                      CacheProperties cacheProperties) {
        this.clusterRegistry = clusterRegistry;
        this.clusterRingManager = clusterRingManager;
        this.cacheProperties = cacheProperties;
    }

    @Scheduled(fixedDelayString = "${cache.cluster.suspect-threshold-ms:10000}")
    @Override
    public void runDetectionCycle() {
        String selfId = cacheProperties.getNode().getId();
        long suspectThresholdMs = cacheProperties.getCluster().getSuspectThresholdMs();
        long downThresholdMs    = cacheProperties.getCluster().getDownThresholdMs();

        Collection<NodeInfo> allNodes = clusterRegistry.findAll();
        boolean ringDirty = false;

        for (NodeInfo node : allNodes) {
            if (selfId.equals(node.getId())) {
                continue;
            }

            long ageMs = Duration.between(node.getLastHeartbeatAt(), Instant.now()).toMillis();
            NodeStatus currentStatus = node.getStatus();

            if (ageMs > downThresholdMs && currentStatus != NodeStatus.DOWN) {
                node.setStatus(NodeStatus.DOWN);
                ringDirty = true;
                log.warn("NODE DOWN  : id='{}' — no heartbeat for {}ms (threshold={}ms). " +
                         "Removing from routing ring.",
                        node.getId(), ageMs, downThresholdMs);

            } else if (ageMs > suspectThresholdMs && currentStatus == NodeStatus.UP) {
                node.setStatus(NodeStatus.SUSPECT);
                ringDirty = true;
                log.warn("NODE SUSPECT: id='{}' — heartbeat stale by {}ms (threshold={}ms). " +
                         "Removing from routing ring until confirmed alive.",
                        node.getId(), ageMs, suspectThresholdMs);

            } else {
                log.trace("Node '{}' is {} — heartbeat age={}ms", node.getId(), currentStatus, ageMs);
            }
        }

        if (ringDirty) {
            log.info("Cluster topology changed — rebuilding consistent hash ring.");
            clusterRingManager.rebuildRing();
        }
    }
}
