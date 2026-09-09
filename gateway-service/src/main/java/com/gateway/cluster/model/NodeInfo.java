package com.gateway.cluster.model;

import java.time.Instant;

public class NodeInfo {
    private String id;
    private String host;
    private int port;
    private Instant registeredAt;
    private NodeStatus status;
    private Instant lastHeartbeatAt;

    public NodeInfo() {}

    public NodeInfo(String id, String host, int port, NodeStatus status) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.registeredAt = Instant.now();
        this.status = status;
        this.lastHeartbeatAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(Instant lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }

    public String getBaseUrl() {
        return "http://" + host + ":" + port;
    }
}
