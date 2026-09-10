package com.cache.cluster.registry;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class InMemoryClusterRegistry implements ClusterRegistry {

    private static final Logger log = LoggerFactory.getLogger(InMemoryClusterRegistry.class);

    private final ConcurrentHashMap<String, NodeInfo> nodes = new ConcurrentHashMap<>();

    @Override
    public void register(NodeInfo node) {
        NodeInfo existing = nodes.put(node.getId(), node);
        if (existing == null) {
            log.info("NODE REGISTERED: id='{}' host='{}' port={} status={}",
                    node.getId(), node.getHost(), node.getPort(), node.getStatus());
        } else {
            log.info("NODE RE-REGISTERED: id='{}' (replaced status={})",
                    node.getId(), existing.getStatus());
        }
    }

    @Override
    public boolean deregister(String nodeId) {
        NodeInfo removed = nodes.remove(nodeId);
        if (removed != null) {
            log.info("NODE DEREGISTERED: id='{}'", nodeId);
            return true;
        }
        log.warn("DEREGISTER: node id='{}' not found in registry", nodeId);
        return false;
    }

    @Override
    public Optional<NodeInfo> findById(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    @Override
    public Collection<NodeInfo> findAll() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    @Override
    public Collection<NodeInfo> findByStatus(NodeStatus status) {
        return nodes.values().stream()
                .filter(node -> node.getStatus() == status)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean exists(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    @Override
    public int size() {
        return nodes.size();
    }
}
