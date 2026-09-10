package com.cache.cluster.routing;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.config.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class ClusterRingManager {

    private static final Logger log = LoggerFactory.getLogger(ClusterRingManager.class);

    private final ClusterRegistry clusterRegistry;
    private final int virtualNodesPerNode;

    private volatile ConsistentHashRing ring;

    public ClusterRingManager(ClusterRegistry clusterRegistry,
                              CacheProperties cacheProperties) {
        this.clusterRegistry = clusterRegistry;
        this.virtualNodesPerNode = cacheProperties.getCluster().getVirtualNodesPerNode();
        this.ring = new ConsistentHashRing(java.util.Collections.emptyList(), virtualNodesPerNode);
    }

    public ConsistentHashRing getRing() {
        return ring;
    }

    public synchronized void rebuildRing() {
        Collection<NodeInfo> upNodes = clusterRegistry.findByStatus(NodeStatus.UP);
        ConsistentHashRing newRing = new ConsistentHashRing(upNodes, virtualNodesPerNode);
        this.ring = newRing;
        log.info("Consistent hash ring rebuilt: {} UP node(s) on ring — {}",
                newRing.size(),
                upNodes.stream().map(NodeInfo::getId).toList());
    }
}
