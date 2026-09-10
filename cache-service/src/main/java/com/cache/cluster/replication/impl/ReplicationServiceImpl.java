package com.cache.cluster.replication.impl;

import com.cache.cluster.forwarding.RequestForwardingService;
import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.cluster.replication.ReplicationService;
import com.cache.cluster.replication.ReplicationStats;
import com.cache.config.CacheProperties;
import com.cache.dto.request.CachePutRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ReplicationServiceImpl implements ReplicationService {

    private static final Logger log = LoggerFactory.getLogger(ReplicationServiceImpl.class);

    private final RequestForwardingService forwardingService;
    private final ClusterRegistry clusterRegistry;
    private final CacheProperties cacheProperties;
    private final Executor executor;

    private final AtomicLong totalAttempted = new AtomicLong(0);
    private final AtomicLong succeeded = new AtomicLong(0);
    private final AtomicLong failed = new AtomicLong(0);

    private final Counter attemptCounter;
    private final Counter successCounter;
    private final Counter failureCounter;

    public ReplicationServiceImpl(
            RequestForwardingService forwardingService,
            ClusterRegistry clusterRegistry,
            CacheProperties cacheProperties,
            @Qualifier("replicationExecutor") Executor executor,
            MeterRegistry meterRegistry) {
        this.forwardingService = forwardingService;
        this.clusterRegistry = clusterRegistry;
        this.cacheProperties = cacheProperties;
        this.executor = executor;

        String nodeId = cacheProperties.getNode().getId();
        this.attemptCounter = meterRegistry.counter("cache.replication.attempts_total", "node", nodeId);
        this.successCounter = meterRegistry.counter("cache.replication.successes_total", "node", nodeId);
        this.failureCounter = meterRegistry.counter("cache.replication.failures_total", "node", nodeId);
    }

    @Override
    public void replicatePutAsync(CachePutRequest request, List<String> replicaNodeIds, String sourceNodeId) {
        if (replicaNodeIds == null || replicaNodeIds.isEmpty()) {
            return;
        }

        boolean async = cacheProperties.getReplication().isAsync();
        int minAckNodes = cacheProperties.getReplication().getMinAckNodes();

        List<String> targetReplicas = replicaNodeIds.stream()
                .filter(id -> !id.equals(sourceNodeId))
                .filter(this::isNodeUp)
                .toList();

        if (!async && targetReplicas.size() < minAckNodes) {
            throw new com.cache.cluster.exception.ReplicationQuorumException(
                    String.format("Sync replication rejected: only %d / %d replicas are available",
                            targetReplicas.size(), minAckNodes)
            );
        }

        log.debug("Initiating PUT replication for key='{}' to replicas={}, async={}, minAckNodes={}",
                request.key(), targetReplicas, async, minAckNodes);

        List<java.util.concurrent.CompletableFuture<Boolean>> futures = new java.util.ArrayList<>();

        for (String replicaId : targetReplicas) {
            totalAttempted.incrementAndGet();
            attemptCounter.increment();

            java.util.concurrent.CompletableFuture<Boolean> future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    log.debug("Executing replicate PUT key='{}' to node '{}'", request.key(), replicaId);
                    forwardingService.replicatePut(replicaId, request, sourceNodeId);
                    succeeded.incrementAndGet();
                    successCounter.increment();
                    log.debug("Successfully replicated PUT key='{}' to node '{}'", request.key(), replicaId);
                    return true;
                } catch (Exception e) {
                    failed.incrementAndGet();
                    failureCounter.increment();
                    log.warn("Failed to replicate PUT key='{}' to node '{}': {}",
                            request.key(), replicaId, e.getMessage());
                    return false;
                }
            }, executor);

            futures.add(future);
        }

        if (!async) {
            long successfulAcks = 0;
            try {
                int timeoutMs = cacheProperties.getCluster().getReadTimeoutMs();
                java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                        .get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("Timeout/Error waiting for PUT replication futures: {}", e.getMessage());
            }

            for (var f : futures) {
                if (f.isDone() && !f.isCompletedExceptionally()) {
                    try {
                        if (f.get()) {
                            successfulAcks++;
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (successfulAcks < minAckNodes) {
                throw new com.cache.cluster.exception.ReplicationQuorumException(
                        String.format("Write replication quorum failed: required %d ACKs, but only received %d",
                                minAckNodes, successfulAcks)
                );
            }
        }
    }

    @Override
    public void replicateDeleteAsync(String key, List<String> replicaNodeIds, String sourceNodeId) {
        if (replicaNodeIds == null || replicaNodeIds.isEmpty()) {
            return;
        }

        boolean async = cacheProperties.getReplication().isAsync();
        int minAckNodes = cacheProperties.getReplication().getMinAckNodes();

        List<String> targetReplicas = replicaNodeIds.stream()
                .filter(id -> !id.equals(sourceNodeId))
                .filter(this::isNodeUp)
                .toList();

        if (!async && targetReplicas.size() < minAckNodes) {
            throw new com.cache.cluster.exception.ReplicationQuorumException(
                    String.format("Sync replication rejected: only %d / %d replicas are available",
                            targetReplicas.size(), minAckNodes)
            );
        }

        log.debug("Initiating DELETE replication for key='{}' to replicas={}, async={}, minAckNodes={}",
                key, targetReplicas, async, minAckNodes);

        List<java.util.concurrent.CompletableFuture<Boolean>> futures = new java.util.ArrayList<>();

        for (String replicaId : targetReplicas) {
            totalAttempted.incrementAndGet();
            attemptCounter.increment();

            java.util.concurrent.CompletableFuture<Boolean> future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    log.debug("Executing replicate DELETE key='{}' to node '{}'", key, replicaId);
                    forwardingService.replicateDelete(replicaId, key, sourceNodeId);
                    succeeded.incrementAndGet();
                    successCounter.increment();
                    log.debug("Successfully replicated DELETE key='{}' to node '{}'", key, replicaId);
                    return true;
                } catch (Exception e) {
                    failed.incrementAndGet();
                    failureCounter.increment();
                    log.warn("Failed to replicate DELETE key='{}' to node '{}': {}",
                            key, replicaId, e.getMessage());
                    return false;
                }
            }, executor);

            futures.add(future);
        }

        if (!async) {
            long successfulAcks = 0;
            try {
                int timeoutMs = cacheProperties.getCluster().getReadTimeoutMs();
                java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                        .get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("Timeout/Error waiting for DELETE replication futures: {}", e.getMessage());
            }

            for (var f : futures) {
                if (f.isDone() && !f.isCompletedExceptionally()) {
                    try {
                        if (f.get()) {
                            successfulAcks++;
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (successfulAcks < minAckNodes) {
                throw new com.cache.cluster.exception.ReplicationQuorumException(
                        String.format("Delete replication quorum failed: required %d ACKs, but only received %d",
                                minAckNodes, successfulAcks)
                );
            }
        }
    }

    @Override
    public ReplicationStats getStats() {
        return ReplicationStats.of(totalAttempted.get(), succeeded.get(), failed.get());
    }

    private boolean isNodeUp(String nodeId) {
        return clusterRegistry.findById(nodeId)
                .map(node -> node.getStatus() == NodeStatus.UP)
                .orElse(false);
    }
}
