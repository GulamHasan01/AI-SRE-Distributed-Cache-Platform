package com.cache.cluster.service;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.dto.request.NodeRegistrationRequest;
import com.cache.dto.response.ClusterStatusResponse;
import com.cache.dto.response.NodeInfoResponse;

import java.util.List;

public interface ClusterRegistryService {

    NodeInfoResponse register(NodeRegistrationRequest request);

    void deregister(String nodeId);

    NodeInfoResponse getNode(String nodeId);

    List<NodeInfoResponse> getAllNodes();

    List<NodeInfoResponse> getNodesByStatus(NodeStatus status);

    ClusterStatusResponse getClusterStatus();

    void markNodeUp(String nodeId);

    void recordHeartbeat(String nodeId);

    List<com.cache.dto.response.VirtualNodeResponse> getRingMapping();

    NodeInfoResponse updateNodeStatus(String nodeId, NodeStatus status);
}
