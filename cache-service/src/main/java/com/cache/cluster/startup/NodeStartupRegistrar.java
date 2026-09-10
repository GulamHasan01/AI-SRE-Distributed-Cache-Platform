package com.cache.cluster.startup;

import com.cache.cluster.routing.ClusterRingManager;
import com.cache.cluster.service.ClusterRegistryService;
import com.cache.config.CacheProperties;
import com.cache.dto.request.NodeRegistrationRequest;
import com.cache.dto.response.NodeInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class NodeStartupRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NodeStartupRegistrar.class);

    private final ClusterRegistryService clusterRegistryService;
    private final CacheProperties cacheProperties;
    private final ClusterRingManager clusterRingManager;

    public NodeStartupRegistrar(ClusterRegistryService clusterRegistryService,
            CacheProperties cacheProperties,
            ClusterRingManager clusterRingManager) {
        this.clusterRegistryService = clusterRegistryService;
        this.cacheProperties = cacheProperties;
        this.clusterRingManager = clusterRingManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        CacheProperties.NodeProperties nodeConfig = cacheProperties.getNode();

        log.info("=== NODE STARTUP: Registering self with cluster registry ===");
        log.info("Node: id='{}' host='{}' port={}",
                nodeConfig.getId(), nodeConfig.getHost(), nodeConfig.getPort());

        try {
            NodeRegistrationRequest request = new NodeRegistrationRequest(
                    nodeConfig.getId(),
                    nodeConfig.getHost(),
                    nodeConfig.getPort());

            NodeInfoResponse registered = clusterRegistryService.register(request);
            log.info("Self-registration successful: id='{}' status={}",
                    registered.nodeId(), registered.status());

            clusterRegistryService.markNodeUp(nodeConfig.getId());
            log.info("=== NODE READY: id='{}' is now UP and serving requests ===",
                    nodeConfig.getId());

            clusterRingManager.rebuildRing();
            log.info("Initial consistent hash ring built.");

            java.util.List<String> peers = cacheProperties.getCluster().getPeers();
            if (peers != null) {
                for (String peerStr : peers) {
                    try {
                        String cleanPeer = peerStr;
                        if (!cleanPeer.startsWith("http://") && !cleanPeer.startsWith("https://")) {
                            cleanPeer = "http://" + cleanPeer;
                        }
                        java.net.URI uri = java.net.URI.create(cleanPeer);

                        String host = uri.getHost();
                        int port = uri.getPort();

                        if (host == null || port == -1) {
                            throw new IllegalArgumentException("Peer address must contain both host and port");
                        }

                        String peerId = deriveNodeId(host, port);

                        if (!nodeConfig.getId().equals(peerId)) {
                            NodeRegistrationRequest peerReq = new NodeRegistrationRequest(peerId, host, port);
                            clusterRegistryService.register(peerReq);
                            log.info("Pre-registered peer node: id='{}' at {}:{}", peerId, host, port);
                        }
                    } catch (Exception ex) {
                        log.error("Failed to pre-register peer '{}' on startup: {}", peerStr, ex.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Self-registration failed for node id='{}': {}",
                    nodeConfig.getId(), e.getMessage(), e);
        }
    }

    private String deriveNodeId(String host, int port) {
        if (port >= 8081 && port <= 8090) {
            return "node-" + (port - 8080);
        }
        return "node-" + host + "-" + port;
    }
}
