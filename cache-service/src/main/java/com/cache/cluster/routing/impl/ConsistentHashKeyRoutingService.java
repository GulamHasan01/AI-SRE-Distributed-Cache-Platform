package com.cache.cluster.routing.impl;

import com.cache.cluster.routing.ClusterRingManager;
import com.cache.cluster.routing.ConsistentHashRing;
import com.cache.cluster.routing.KeyRoutingService;
import com.cache.config.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConsistentHashKeyRoutingService implements KeyRoutingService {

    private static final Logger log = LoggerFactory.getLogger(ConsistentHashKeyRoutingService.class);

    private final ClusterRingManager clusterRingManager;
    private final CacheProperties cacheProperties;

    public ConsistentHashKeyRoutingService(ClusterRingManager clusterRingManager,
                                           CacheProperties cacheProperties) {
        this.clusterRingManager = clusterRingManager;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public String getOwnerNodeId(String key) {
        ConsistentHashRing ring = clusterRingManager.getRing();

        String owner = ring.getNodeForKey(key)
                .orElse(cacheProperties.getNode().getId());

        log.debug("Hash-routing key='{}' -> owner='{}' (ring={} UP nodes)",
                key, owner, ring.size());
        return owner;
    }

    @Override
    public List<String> getRouteList(String key) {
        ConsistentHashRing ring = clusterRingManager.getRing();
        int replicationFactor = cacheProperties.getCluster().getReplicationFactor();

        List<String> routes = ring.getNodesForKey(key, replicationFactor);

        if (routes.isEmpty()) {
            routes.add(cacheProperties.getNode().getId());
        }
        return routes;
    }

    @Override
    public List<String> getReplicaNodeIds(String key) {
        List<String> routes = getRouteList(key);
        if (routes.size() <= 1) {
            return java.util.Collections.emptyList();
        }
        return routes.subList(1, routes.size());
    }
}