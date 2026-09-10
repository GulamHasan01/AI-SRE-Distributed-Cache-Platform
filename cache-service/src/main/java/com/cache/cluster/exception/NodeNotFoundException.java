package com.cache.cluster.exception;

public class NodeNotFoundException extends RuntimeException {

    private final String nodeId;

    public NodeNotFoundException(String nodeId) {
        super("Node not found in cluster registry: id='" + nodeId + "'");
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }
}
