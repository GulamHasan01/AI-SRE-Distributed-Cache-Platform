package com.cache.cluster.heartbeat.impl;

import com.cache.cluster.heartbeat.HeartbeatService;
import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.cluster.service.ClusterRegistryService;
import com.cache.config.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class HttpHeartbeatService implements HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HttpHeartbeatService.class);

    private static final String PING_PATH = "/api/v1/cluster/health/ping";

    private final ClusterRegistry clusterRegistry;
    private final ClusterRegistryService clusterRegistryService;
    private final CacheProperties cacheProperties;
    private final RestClient heartbeatClient;

    public HttpHeartbeatService(ClusterRegistry clusterRegistry,
                                ClusterRegistryService clusterRegistryService,
                                CacheProperties cacheProperties,
                                @Qualifier("heartbeatRestClient") RestClient heartbeatClient) {
        this.clusterRegistry = clusterRegistry;
        this.clusterRegistryService = clusterRegistryService;
        this.cacheProperties = cacheProperties;
        this.heartbeatClient = heartbeatClient;
    }

    @Scheduled(fixedDelayString = "${cache.cluster.heartbeat-interval-ms:5000}")
    @Override
    public void emitHeartbeats() {
        String selfId = cacheProperties.getNode().getId();

        clusterRegistry.findAll().forEach(node -> {
            if (selfId.equals(node.getId())) {
                return;
            }
            boolean alive = pingNode(node);
            if (alive) {
                try {
                    clusterRegistryService.recordHeartbeat(node.getId());
                    log.debug("Heartbeat OK: node='{}' at {}", node.getId(), node.getBaseUrl());
                } catch (Exception e) {
                    log.warn("Failed to record heartbeat for node='{}': {}", node.getId(), e.getMessage());
                }
            } else {
                log.warn("Heartbeat MISSED: node='{}' at {} did not respond",
                        node.getId(), node.getBaseUrl());
            }
        });
    }

    @Override
    public boolean pingNode(NodeInfo node) {
        try {
            heartbeatClient.get()
                    .uri(node.getBaseUrl() + PING_PATH)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.trace("Ping failed for node='{}': {}", node.getId(), e.getMessage());
            return false;
        }
    }
}
