package com.cache.cluster.registry;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;

import java.util.Collection;
import java.util.Optional;

public interface ClusterRegistry {

    void register(NodeInfo node);

    boolean deregister(String nodeId);

    Optional<NodeInfo> findById(String nodeId);

    Collection<NodeInfo> findAll();

    Collection<NodeInfo> findByStatus(NodeStatus status);

    boolean exists(String nodeId);

    int size();
}
