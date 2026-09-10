package com.cache.cluster.model;

import java.time.Instant;

public class NodeInfo {

    private final String id;
    private final String host;
    private final int port;
    private final Instant registeredAt;

    private volatile NodeStatus status;

    private volatile Instant lastHeartbeatAt;

    public NodeInfo(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.registeredAt = Instant.now();
        this.status = NodeStatus.STARTING;
        this.lastHeartbeatAt = Instant.now();
    }

    public void markUp() {
        this.status = NodeStatus.UP;
        this.lastHeartbeatAt = Instant.now();
    }

    public void recordHeartbeat() {
        this.lastHeartbeatAt = Instant.now();
        if (this.status == NodeStatus.SUSPECT || this.status == NodeStatus.STARTING || this.status == NodeStatus.DOWN) {
            this.status = NodeStatus.UP;
        }
    }

    public String getBaseUrl() {
        return "http://" + host + ":" + port;
    }

    public boolean isAvailable() {
        return status == NodeStatus.UP;
    }

    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public Instant getRegisteredAt() { return registeredAt; }
    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }

    @Override
    public String toString() {
        return "NodeInfo{id='" + id + "', host='" + host + "', port=" + port +
               ", status=" + status + ", registeredAt=" + registeredAt + "}";
    }
}
