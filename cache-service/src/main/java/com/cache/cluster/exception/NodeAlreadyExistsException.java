package com.cache.cluster.exception;

public class NodeAlreadyExistsException extends RuntimeException {

    private final String nodeId;

    public NodeAlreadyExistsException(String nodeId) {
        super("Node with id='" + nodeId + "' is already registered in the cluster");
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }
}
