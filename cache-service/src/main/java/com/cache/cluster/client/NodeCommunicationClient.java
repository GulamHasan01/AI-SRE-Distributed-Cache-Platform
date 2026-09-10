package com.cache.cluster.client;

import com.cache.cluster.model.NodeInfo;
import com.cache.dto.request.CachePutRequest;
import com.cache.dto.response.CacheEntryResponse;

public interface NodeCommunicationClient {

    CacheEntryResponse get(NodeInfo targetNode, String key);

    CacheEntryResponse put(NodeInfo targetNode, CachePutRequest request);

    CacheEntryResponse putReplicated(NodeInfo targetNode, CachePutRequest request, String sourceNodeId);

    void delete(NodeInfo targetNode, String key);

    void deleteReplicated(NodeInfo targetNode, String key, String sourceNodeId);
}
