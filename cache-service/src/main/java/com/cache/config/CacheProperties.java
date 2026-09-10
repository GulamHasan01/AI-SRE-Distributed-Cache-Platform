package com.cache.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {

    @Min(value = 1, message = "Cache max-size must be at least 1")
    private int maxSize = 10000;

    @NotNull
    private EvictionPolicyType evictionPolicy = EvictionPolicyType.LRU;

    @NotNull
    private TtlProperties ttl = new TtlProperties();

    @NotNull
    private NodeProperties node = new NodeProperties();

    @NotNull
    private ClusterProperties cluster = new ClusterProperties();

    @NotNull
    private ReplicationProperties replication = new ReplicationProperties();

    @NotNull
    private PersistenceProperties persistence = new PersistenceProperties();

    public enum EvictionPolicyType {
        LRU,
        NO_EVICTION
    }

    public int getMaxSize() { return maxSize; }
    public void setMaxSize(int maxSize) { this.maxSize = maxSize; }

    public EvictionPolicyType getEvictionPolicy() { return evictionPolicy; }
    public void setEvictionPolicy(EvictionPolicyType evictionPolicy) { this.evictionPolicy = evictionPolicy; }

    public TtlProperties getTtl() { return ttl; }
    public void setTtl(TtlProperties ttl) { this.ttl = ttl; }

    public NodeProperties getNode() { return node; }
    public void setNode(NodeProperties node) { this.node = node; }

    public ClusterProperties getCluster() { return cluster; }
    public void setCluster(ClusterProperties cluster) { this.cluster = cluster; }

    public ReplicationProperties getReplication() { return replication; }
    public void setReplication(ReplicationProperties replication) { this.replication = replication; }

    public PersistenceProperties getPersistence() { return persistence; }
    public void setPersistence(PersistenceProperties persistence) { this.persistence = persistence; }

    public static class PersistenceProperties {

        private boolean enabled = true;

        @NotBlank(message = "Snapshot filePath must not be blank")
        private String filePath = "./data/cache-snapshot.json";

        @Min(value = 1, message = "Snapshot interval must be at least 1 second")
        private long snapshotIntervalSeconds = 60;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public long getSnapshotIntervalSeconds() { return snapshotIntervalSeconds; }
        public void setSnapshotIntervalSeconds(long snapshotIntervalSeconds) { this.snapshotIntervalSeconds = snapshotIntervalSeconds; }
    }

    public static class TtlProperties {

        private long defaultSeconds = -1;

        @Min(value = 100, message = "Sweep interval must be at least 100ms")
        private long sweepIntervalMs = 5000;

        public long getDefaultSeconds() { return defaultSeconds; }
        public void setDefaultSeconds(long defaultSeconds) { this.defaultSeconds = defaultSeconds; }

        public long getSweepIntervalMs() { return sweepIntervalMs; }
        public void setSweepIntervalMs(long sweepIntervalMs) { this.sweepIntervalMs = sweepIntervalMs; }
    }

    public static class NodeProperties {

        @NotBlank(message = "Node id must not be blank")
        private String id = "node-1";

        @NotBlank(message = "Node host must not be blank")
        private String host = "localhost";

        @Min(value = 1, message = "Node port must be >= 1")
        private int port = 8081;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }

    public static class ClusterProperties {

        @Min(value = 100, message = "Connect timeout must be at least 100ms")
        private int connectTimeoutMs = 2000;

        @Min(value = 100, message = "Read timeout must be at least 100ms")
        private int readTimeoutMs = 5000;

        @Min(value = 0, message = "Max retries must be >= 0")
        private int maxRetries = 2;

        @Min(value = 1, message = "Virtual nodes per node must be at least 1")
        private int virtualNodesPerNode = 150;

        @Min(value = 1, message = "Replication factor must be at least 1")
        private int replicationFactor = 2;

        @Min(value = 500, message = "Heartbeat interval must be at least 500ms")
        private long heartbeatIntervalMs = 5000;

        @Min(value = 100, message = "Heartbeat timeout must be at least 100ms")
        private long heartbeatTimeoutMs = 2000;

        @Min(value = 1000, message = "Suspect threshold must be at least 1000ms")
        private long suspectThresholdMs = 10000;

        @Min(value = 1000, message = "Down threshold must be at least 1000ms")
        private long downThresholdMs = 20000;

        private java.util.List<String> peers = new java.util.ArrayList<>();

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

        public int getVirtualNodesPerNode() { return virtualNodesPerNode; }
        public void setVirtualNodesPerNode(int virtualNodesPerNode) { this.virtualNodesPerNode = virtualNodesPerNode; }

        public int getReplicationFactor() { return replicationFactor; }
        public void setReplicationFactor(int replicationFactor) { this.replicationFactor = replicationFactor; }

        public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
        public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }

        public long getHeartbeatTimeoutMs() { return heartbeatTimeoutMs; }
        public void setHeartbeatTimeoutMs(long heartbeatTimeoutMs) { this.heartbeatTimeoutMs = heartbeatTimeoutMs; }

        public long getSuspectThresholdMs() { return suspectThresholdMs; }
        public void setSuspectThresholdMs(long suspectThresholdMs) { this.suspectThresholdMs = suspectThresholdMs; }

        public long getDownThresholdMs() { return downThresholdMs; }
        public void setDownThresholdMs(long downThresholdMs) { this.downThresholdMs = downThresholdMs; }

        public java.util.List<String> getPeers() { return peers; }
        public void setPeers(java.util.List<String> peers) { this.peers = peers; }
    }

    public static class ReplicationProperties {

        private boolean async = true;

        @Min(value = 0, message = "minAckNodes must be >= 0")
        private int minAckNodes = 1;

        @Min(value = 1, message = "threadPoolSize must be at least 1")
        private int threadPoolSize = 4;

        public boolean isAsync() { return async; }
        public void setAsync(boolean async) { this.async = async; }

        public int getMinAckNodes() { return minAckNodes; }
        public void setMinAckNodes(int minAckNodes) { this.minAckNodes = minAckNodes; }

        public int getThreadPoolSize() { return threadPoolSize; }
        public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = threadPoolSize; }
    }
}
