package com.cache.cluster.replication;

import com.cache.dto.request.CachePutRequest;

public interface ReplicationService {

    void replicatePutAsync(CachePutRequest request, java.util.List<String> replicaNodeIds, String sourceNodeId);

    void replicateDeleteAsync(String key, java.util.List<String> replicaNodeIds, String sourceNodeId);

    ReplicationStats getStats();
}
