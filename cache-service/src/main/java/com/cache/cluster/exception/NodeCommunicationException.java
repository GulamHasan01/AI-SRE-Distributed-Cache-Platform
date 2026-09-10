package com.cache.cluster.exception;

public class NodeCommunicationException extends RuntimeException {

    private final String targetNodeId;
    private final String targetUrl;

    public NodeCommunicationException(String targetNodeId, String targetUrl, String message) {
        super(String.format("Communication failure with node '%s' at '%s': %s",
                targetNodeId, targetUrl, message));
        this.targetNodeId = targetNodeId;
        this.targetUrl = targetUrl;
    }

    public NodeCommunicationException(String targetNodeId, String targetUrl,
                                      String message, Throwable cause) {
        super(String.format("Communication failure with node '%s' at '%s': %s",
                targetNodeId, targetUrl, message), cause);
        this.targetNodeId = targetNodeId;
        this.targetUrl = targetUrl;
    }

    public String getTargetNodeId() { return targetNodeId; }
    public String getTargetUrl() { return targetUrl; }
}
