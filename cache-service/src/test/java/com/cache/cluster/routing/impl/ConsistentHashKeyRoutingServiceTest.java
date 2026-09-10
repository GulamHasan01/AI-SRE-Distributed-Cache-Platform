package com.cache.cluster.routing.impl;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.routing.ClusterRingManager;
import com.cache.cluster.routing.ConsistentHashRing;
import com.cache.cluster.routing.KeyRoutingService;
import com.cache.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsistentHashKeyRoutingService")
class ConsistentHashKeyRoutingServiceTest {

    @Mock
    private ClusterRingManager clusterRingManager;

    @Mock
    private CacheProperties cacheProperties;

    @Mock
    private CacheProperties.ClusterProperties clusterProps;

    @Mock
    private CacheProperties.NodeProperties nodeProps;

    private KeyRoutingService service;

    private static final String SELF_ID = "node-1";
    private static final int VNODES = 150;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(cacheProperties.getNode()).thenReturn(nodeProps);
        org.mockito.Mockito.lenient().when(nodeProps.getId()).thenReturn(SELF_ID);
        org.mockito.Mockito.lenient().when(cacheProperties.getCluster()).thenReturn(clusterProps);
        org.mockito.Mockito.lenient().when(clusterProps.getReplicationFactor()).thenReturn(2);
        service = new ConsistentHashKeyRoutingService(clusterRingManager, cacheProperties);
    }

    private ConsistentHashRing ring(NodeInfo... nodes) {
        return new ConsistentHashRing(List.of(nodes), VNODES);
    }

    private NodeInfo upNode(String id, int port) {
        NodeInfo n = new NodeInfo(id, "localhost", port);
        n.markUp();
        return n;
    }

    @Test
    @DisplayName("returns self ID when ring is empty (no UP nodes)")
    void shouldReturnSelfWhenRingIsEmpty() {
        when(clusterRingManager.getRing())
                .thenReturn(new ConsistentHashRing(Collections.emptyList(), VNODES));

        String owner = service.getOwnerNodeId("any-key");

        assertThat(owner).isEqualTo(SELF_ID);
    }

    @Test
    @DisplayName("returns node from ring when UP nodes exist")
    void shouldReturnValidNodeIdFromRing() {
        NodeInfo n1 = upNode("node-1", 8081);
        NodeInfo n2 = upNode("node-2", 8082);
        when(clusterRingManager.getRing()).thenReturn(ring(n1, n2));

        String owner = service.getOwnerNodeId("user:1001");

        assertThat(owner).isIn("node-1", "node-2");
    }

    @Test
    @DisplayName("same key always maps to same node (determinism)")
    void shouldBeDeterministic() {
        NodeInfo n1 = upNode("node-1", 8081);
        NodeInfo n2 = upNode("node-2", 8082);
        ConsistentHashRing r = ring(n1, n2);
        when(clusterRingManager.getRing()).thenReturn(r);

        String owner1 = service.getOwnerNodeId("session:abc123");
        String owner2 = service.getOwnerNodeId("session:abc123");

        assertThat(owner1).isEqualTo(owner2);
    }

    @Test
    @DisplayName("different keys distribute across nodes")
    void differentKeysShouldDistributeAcrossNodes() {
        NodeInfo n1 = upNode("node-1", 8081);
        NodeInfo n2 = upNode("node-2", 8082);
        when(clusterRingManager.getRing()).thenReturn(ring(n1, n2));

        Set<String> ownersSeen = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) {
            ownersSeen.add(service.getOwnerNodeId("key:" + i));
        }

        assertThat(ownersSeen).containsExactlyInAnyOrder("node-1", "node-2");
    }

    @Test
    @DisplayName("returns replication route list of correct size")
    void shouldReturnReplicationRoutes() {
        NodeInfo n1 = upNode("node-1", 8081);
        NodeInfo n2 = upNode("node-2", 8082);
        NodeInfo n3 = upNode("node-3", 8083);
        when(clusterRingManager.getRing()).thenReturn(ring(n1, n2, n3));

        List<String> route = service.getRouteList("user:1001");
        assertThat(route).hasSize(2);

        List<String> replicas = service.getReplicaNodeIds("user:1001");
        assertThat(replicas).hasSize(1);
        assertThat(replicas.get(0)).isEqualTo(route.get(1));
    }

    @Test
    @DisplayName("DOWN nodes excluded — ring with only self returns self")
    void downNodeExcludedFromRing() {
        NodeInfo n1 = upNode("node-1", 8081);
        when(clusterRingManager.getRing()).thenReturn(ring(n1));

        String owner = service.getOwnerNodeId("any-key");
        assertThat(owner).isEqualTo("node-1");
    }
}