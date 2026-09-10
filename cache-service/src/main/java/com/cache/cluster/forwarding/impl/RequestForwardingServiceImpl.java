package com.cache.cluster.forwarding.impl;

import com.cache.cluster.client.NodeCommunicationClient;
import com.cache.cluster.exception.NodeNotFoundException;
import com.cache.cluster.forwarding.RequestForwardingService;
import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.dto.request.CachePutRequest;
import com.cache.dto.response.CacheEntryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RequestForwardingServiceImpl implements RequestForwardingService {

    private static final Logger log = LoggerFactory.getLogger(RequestForwardingServiceImpl.class);

    private final ClusterRegistry clusterRegistry;
    private final NodeCommunicationClient communicationClient;

    public RequestForwardingServiceImpl(ClusterRegistry clusterRegistry,
                                         NodeCommunicationClient communicationClient) {
        this.clusterRegistry = clusterRegistry;
        this.communicationClient = communicationClient;
    }

    @Override
    public CacheEntryResponse forwardGet(String targetNodeId, String key) {
        NodeInfo targetNode = getNodeOrThrow(targetNodeId);
        log.info("FORWARD GET key='{}' → node='{}' at '{}'", key, targetNodeId, targetNode.getBaseUrl());
        return communicationClient.get(targetNode, key);
    }

    @Override
    public CacheEntryResponse forwardPut(String targetNodeId, CachePutRequest request) {
        NodeInfo targetNode = getNodeOrThrow(targetNodeId);
        log.info("FORWARD PUT key='{}' → node='{}' at '{}'", request.key(), targetNodeId, targetNode.getBaseUrl());
        return communicationClient.put(targetNode, request);
    }

    @Override
    public void forwardDelete(String targetNodeId, String key) {
        NodeInfo targetNode = getNodeOrThrow(targetNodeId);
        log.info("FORWARD DELETE key='{}' → node='{}' at '{}'", key, targetNodeId, targetNode.getBaseUrl());
        communicationClient.delete(targetNode, key);
    }

    @Override
    public CacheEntryResponse replicatePut(String targetNodeId, CachePutRequest request, String sourceNodeId) {
        NodeInfo targetNode = getNodeOrThrow(targetNodeId);
        log.info("REPLICATE PUT key='{}' → node='{}' (from '{}')", request.key(), targetNodeId, sourceNodeId);
        return communicationClient.putReplicated(targetNode, request, sourceNodeId);
    }

    @Override
    public void replicateDelete(String targetNodeId, String key, String sourceNodeId) {
        NodeInfo targetNode = getNodeOrThrow(targetNodeId);
        log.info("REPLICATE DELETE key='{}' → node='{}' (from '{}')", key, targetNodeId, sourceNodeId);
        communicationClient.deleteReplicated(targetNode, key, sourceNodeId);
    }

    private NodeInfo getNodeOrThrow(String nodeId) {
        return clusterRegistry.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
    }
}
