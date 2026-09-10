package com.cache.cluster.replication.impl;

import com.cache.cluster.forwarding.RequestForwardingService;
import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.cluster.replication.ReplicationStats;
import com.cache.config.CacheProperties;
import com.cache.dto.request.CachePutRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReplicationServiceImpl Unit Tests")
class ReplicationServiceImplTest {

    @Mock
    private RequestForwardingService forwardingService;

    @Mock
    private ClusterRegistry clusterRegistry;

    @Mock
    private CacheProperties cacheProperties;

    @Mock
    private CacheProperties.ReplicationProperties replicationProperties;

    private Executor directExecutor = Runnable::run;

    private io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    private ReplicationServiceImpl replicationService;

    private final String selfId = "node-1";
    private final String peerId1 = "node-2";
    private final String peerId2 = "node-3";

    @BeforeEach
    void setUp() {
        CacheProperties.NodeProperties nodeProperties = mock(CacheProperties.NodeProperties.class);
        lenient().when(cacheProperties.getNode()).thenReturn(nodeProperties);
        lenient().when(nodeProperties.getId()).thenReturn(selfId);
        lenient().when(cacheProperties.getReplication()).thenReturn(replicationProperties);
        lenient().when(replicationProperties.getMinAckNodes()).thenReturn(1);
        replicationService = new ReplicationServiceImpl(
                forwardingService,
                clusterRegistry,
                cacheProperties,
                directExecutor,
                meterRegistry
        );
    }

    private NodeInfo createNode(String id, NodeStatus status) {
        NodeInfo node = new NodeInfo(id, "localhost", 8080);
        node.setStatus(status);
        return node;
    }

    @Test
    @DisplayName("replicatePutAsync - empty target replicas list does nothing")
    void replicatePutEmptyListDoesNothing() {
        CachePutRequest request = new CachePutRequest("k", "v", null);
        replicationService.replicatePutAsync(request, Collections.emptyList(), selfId);

        verifyNoInteractions(forwardingService, clusterRegistry);
        ReplicationStats stats = replicationService.getStats();
        assertThat(stats.totalAttempted()).isEqualTo(0);
    }

    @Test
    @DisplayName("replicatePutAsync - ignores self node and non-existent nodes")
    void replicatePutIgnoresSelfAndNonExistent() {
        CachePutRequest request = new CachePutRequest("k", "v", null);
        when(replicationProperties.isAsync()).thenReturn(true);
        when(clusterRegistry.findById(peerId1)).thenReturn(Optional.empty());

        replicationService.replicatePutAsync(request, List.of(selfId, peerId1), selfId);

        verifyNoInteractions(forwardingService);
        ReplicationStats stats = replicationService.getStats();
        assertThat(stats.totalAttempted()).isEqualTo(0);
    }

    @Test
    @DisplayName("replicatePutAsync - skips DOWN or SUSPECT nodes")
    void replicatePutSkipsDownOrSuspectNodes() {
        CachePutRequest request = new CachePutRequest("k", "v", null);
        when(replicationProperties.isAsync()).thenReturn(true);

        NodeInfo node2 = createNode(peerId1, NodeStatus.DOWN);
        NodeInfo node3 = createNode(peerId2, NodeStatus.SUSPECT);

        when(clusterRegistry.findById(peerId1)).thenReturn(Optional.of(node2));
        when(clusterRegistry.findById(peerId2)).thenReturn(Optional.of(node3));

        replicationService.replicatePutAsync(request, List.of(peerId1, peerId2), selfId);

        verifyNoInteractions(forwardingService);
        ReplicationStats stats = replicationService.getStats();
        assertThat(stats.totalAttempted()).isEqualTo(0);
    }

    @Test
    @DisplayName("replicatePutAsync - successful replicate to UP nodes increments metrics")
    void replicatePutSuccess() {
        CachePutRequest request = new CachePutRequest("k", "v", null);
        when(replicationProperties.isAsync()).thenReturn(false);

        NodeInfo node2 = createNode(peerId1, NodeStatus.UP);
        when(clusterRegistry.findById(peerId1)).thenReturn(Optional.of(node2));
        when(forwardingService.replicatePut(eq(peerId1), eq(request), eq(selfId)))
                .thenReturn(mock(com.cache.dto.response.CacheEntryResponse.class));

        replicationService.replicatePutAsync(request, List.of(peerId1), selfId);

        verify(forwardingService).replicatePut(eq(peerId1), eq(request), eq(selfId));
        ReplicationStats stats = replicationService.getStats();
        assertThat(stats.totalAttempted()).isEqualTo(1);
        assertThat(stats.succeeded()).isEqualTo(1);
        assertThat(stats.failed()).isEqualTo(0);
        assertThat(stats.successRatePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("replicatePutAsync - failed replication call increments failed count without throwing exception")
    void replicatePutFailure() {
        CachePutRequest request = new CachePutRequest("k", "v", null);
        when(replicationProperties.isAsync()).thenReturn(true);

        NodeInfo node2 = createNode(peerId1, NodeStatus.UP);
        when(clusterRegistry.findById(peerId1)).thenReturn(Optional.of(node2));
        doThrow(new RuntimeException("Network timeout")).when(forwardingService)
                .replicatePut(eq(peerId1), eq(request), eq(selfId));

        replicationService.replicatePutAsync(request, List.of(peerId1), selfId);

        ReplicationStats stats = replicationService.getStats();
        assertThat(stats.totalAttempted()).isEqualTo(1);
        assertThat(stats.succeeded()).isEqualTo(0);
        assertThat(stats.failed()).isEqualTo(1);
        assertThat(stats.successRatePercent()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("replicateDeleteAsync - successful replicate to UP nodes increments metrics")
    void replicateDeleteSuccess() {
        when(replicationProperties.isAsync()).thenReturn(false);

        NodeInfo node2 = createNode(peerId1, NodeStatus.UP);
        when(clusterRegistry.findById(peerId1)).thenReturn(Optional.of(node2));

        replicationService.replicateDeleteAsync("k", List.of(peerId1), selfId);

        verify(forwardingService).replicateDelete(eq(peerId1), eq("k"), eq(selfId));
        ReplicationStats stats = replicationService.getStats();
        assertThat(stats.totalAttempted()).isEqualTo(1);
        assertThat(stats.succeeded()).isEqualTo(1);
        assertThat(stats.failed()).isEqualTo(0);
        assertThat(stats.successRatePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("replicateDeleteAsync - failed replication call increments failed count")
    void replicateDeleteFailure() {
        when(replicationProperties.isAsync()).thenReturn(true);

        NodeInfo node2 = createNode(peerId1, NodeStatus.UP);
        when(clusterRegistry.findById(peerId1)).thenReturn(Optional.of(node2));
        doThrow(new RuntimeException("Network error")).when(forwardingService)
                .replicateDelete(eq(peerId1), eq("k"), eq(selfId));

        replicationService.replicateDeleteAsync("k", List.of(peerId1), selfId);

        ReplicationStats stats = replicationService.getStats();
        assertThat(stats.totalAttempted()).isEqualTo(1);
        assertThat(stats.succeeded()).isEqualTo(0);
        assertThat(stats.failed()).isEqualTo(1);
        assertThat(stats.successRatePercent()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("replicatePutAsync - throws ReplicationQuorumException in sync mode when successful ACKs are below minAckNodes")
    void replicatePutQuorumFailure() {
        CachePutRequest request = new CachePutRequest("k", "v", null);
        when(replicationProperties.isAsync()).thenReturn(false);
        when(replicationProperties.getMinAckNodes()).thenReturn(2);

        NodeInfo node2 = createNode(peerId1, NodeStatus.UP);
        NodeInfo node3 = createNode(peerId2, NodeStatus.UP);
        when(clusterRegistry.findById(peerId1)).thenReturn(Optional.of(node2));
        when(clusterRegistry.findById(peerId2)).thenReturn(Optional.of(node3));

        when(forwardingService.replicatePut(eq(peerId1), eq(request), eq(selfId)))
                .thenReturn(mock(com.cache.dto.response.CacheEntryResponse.class));
        doThrow(new RuntimeException("timeout")).when(forwardingService)
                .replicatePut(eq(peerId2), eq(request), eq(selfId));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.cache.cluster.exception.ReplicationQuorumException.class,
                () -> replicationService.replicatePutAsync(request, List.of(peerId1, peerId2), selfId)
        );

        ReplicationStats stats = replicationService.getStats();
        assertThat(stats.totalAttempted()).isEqualTo(2);
        assertThat(stats.succeeded()).isEqualTo(1);
        assertThat(stats.failed()).isEqualTo(1);
    }
}
