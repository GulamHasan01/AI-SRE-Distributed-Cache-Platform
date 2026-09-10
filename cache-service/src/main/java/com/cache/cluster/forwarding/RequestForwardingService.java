package com.cache.cluster.forwarding;

import com.cache.dto.request.CachePutRequest;
import com.cache.dto.response.CacheEntryResponse;

public interface RequestForwardingService {

    CacheEntryResponse forwardGet(String targetNodeId, String key);

    CacheEntryResponse forwardPut(String targetNodeId, CachePutRequest request);

    void forwardDelete(String targetNodeId, String key);

    CacheEntryResponse replicatePut(String targetNodeId, CachePutRequest request, String sourceNodeId);

    void replicateDelete(String targetNodeId, String key, String sourceNodeId);
}
