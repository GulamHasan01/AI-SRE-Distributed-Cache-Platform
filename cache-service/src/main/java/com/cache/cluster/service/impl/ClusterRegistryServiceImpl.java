package com.cache.cluster.service.impl;

import com.cache.cluster.exception.NodeNotFoundException;
import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.cluster.routing.ClusterRingManager;
import com.cache.cluster.service.ClusterRegistryService;
import com.cache.dto.request.NodeRegistrationRequest;
import com.cache.dto.response.ClusterStatusResponse;
import com.cache.dto.response.NodeInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClusterRegistryServiceImpl implements ClusterRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ClusterRegistryServiceImpl.class);

    private final ClusterRegistry clusterRegistry;
    private final ClusterRingManager clusterRingManager;

    public ClusterRegistryServiceImpl(ClusterRegistry clusterRegistry,
                                      ClusterRingManager clusterRingManager) {
        this.clusterRegistry = clusterRegistry;
        this.clusterRingManager = clusterRingManager;
    }

    @Override
    public NodeInfoResponse register(NodeRegistrationRequest request) {
        log.info("Registering node: id='{}' host='{}' port={}",
                request.nodeId(), request.host(), request.port());

        NodeInfo node = new NodeInfo(request.nodeId(), request.host(), request.port());
        clusterRegistry.register(node);

        log.info("Node registered successfully: id='{}' status={}",
                node.getId(), node.getStatus());
        return NodeInfoResponse.from(node);
    }

    @Override
    public void deregister(String nodeId) {
        log.info("Deregistering node: id='{}'", nodeId);
        boolean removed = clusterRegistry.deregister(nodeId);
        if (!removed) {
            throw new NodeNotFoundException(nodeId);
        }
        log.info("Node deregistered: id='{}' — rebuilding ring.", nodeId);
        clusterRingManager.rebuildRing();
    }

    @Override
    public NodeInfoResponse getNode(String nodeId) {
        return clusterRegistry.findById(nodeId)
                .map(NodeInfoResponse::from)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
    }

    @Override
    public List<NodeInfoResponse> getAllNodes() {
        return clusterRegistry.findAll().stream()
                .map(NodeInfoResponse::from)
                .toList();
    }

    @Override
    public List<NodeInfoResponse> getNodesByStatus(NodeStatus status) {
        return clusterRegistry.findByStatus(status).stream()
                .map(NodeInfoResponse::from)
                .toList();
    }

    @Override
    public ClusterStatusResponse getClusterStatus() {
        List<NodeInfoResponse> allNodes = getAllNodes();

        int upCount      = (int) allNodes.stream().filter(n -> n.status() == NodeStatus.UP).count();
        int startCount   = (int) allNodes.stream().filter(n -> n.status() == NodeStatus.STARTING).count();
        int suspectCount = (int) allNodes.stream().filter(n -> n.status() == NodeStatus.SUSPECT).count();
        int downCount    = (int) allNodes.stream().filter(n -> n.status() == NodeStatus.DOWN).count();

        boolean healthy = !allNodes.isEmpty() && upCount == allNodes.size();

        log.debug("Cluster status: total={} up={} starting={} suspect={} down={} healthy={}",
                allNodes.size(), upCount, startCount, suspectCount, downCount, healthy);

        return new ClusterStatusResponse(
                allNodes.size(),
                upCount,
                startCount,
                suspectCount,
                downCount,
                healthy,
                allNodes
        );
    }

    @Override
    public void markNodeUp(String nodeId) {
        NodeInfo node = clusterRegistry.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        node.markUp();
        log.info("Node marked UP: id='{}' — rebuilding ring.", nodeId);
        clusterRingManager.rebuildRing();
    }

    @Override
    public void recordHeartbeat(String nodeId) {
        NodeInfo node = clusterRegistry.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));

        NodeStatus statusBefore = node.getStatus();
        node.recordHeartbeat();
        NodeStatus statusAfter  = node.getStatus();

        if (statusBefore != statusAfter) {
            log.info("NODE RECOVERED: id='{}' transitioned {} → {}", nodeId, statusBefore, statusAfter);
            clusterRingManager.rebuildRing();
        } else {
            log.debug("Heartbeat recorded for node='{}' (status={})", nodeId, statusAfter);
        }
    }

    @Override
    public List<com.cache.dto.response.VirtualNodeResponse> getRingMapping() {
        com.cache.cluster.routing.ConsistentHashRing ring = clusterRingManager.getRing();
        return ring.getRingMap().entrySet().stream()
                .map(entry -> new com.cache.dto.response.VirtualNodeResponse(
                        String.format("%016x", entry.getKey()),
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    @Override
    public NodeInfoResponse updateNodeStatus(String nodeId, NodeStatus status) {
        NodeInfo node = clusterRegistry.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));

        NodeStatus oldStatus = node.getStatus();
        if (oldStatus != status) {
            node.setStatus(status);
            log.info("Node '{}' manually transitioned status from {} to {} — rebuilding ring.", nodeId, oldStatus, status);
            clusterRingManager.rebuildRing();
        }
        return NodeInfoResponse.from(node);
    }
}
