package com.cache.cluster.routing;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClusterRingManager")
class ClusterRingManagerTest {

    @Mock
    private ClusterRegistry clusterRegistry;

    @Mock
    private CacheProperties cacheProperties;

    @Mock
    private CacheProperties.ClusterProperties clusterProps;

    private ClusterRingManager ringManager;

    @BeforeEach
    void setUp() {
        when(cacheProperties.getCluster()).thenReturn(clusterProps);
        when(clusterProps.getVirtualNodesPerNode()).thenReturn(150);
        ringManager = new ClusterRingManager(clusterRegistry, cacheProperties);
    }

    @Test
    @DisplayName("getRing() returns empty ring before first rebuildRing()")
    void initialRingIsEmpty() {
        ConsistentHashRing ring = ringManager.getRing();
        assertThat(ring).isNotNull();
        assertThat(ring.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("rebuildRing() builds ring from UP nodes in registry")
    void rebuildRingIncludesUpNodes() {
        NodeInfo n1 = new NodeInfo("node-1", "localhost", 8081); n1.markUp();
        NodeInfo n2 = new NodeInfo("node-2", "localhost", 8082); n2.markUp();
        when(clusterRegistry.findByStatus(NodeStatus.UP)).thenReturn(List.of(n1, n2));

        ringManager.rebuildRing();

        assertThat(ringManager.getRing().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("rebuildRing() excludes SUSPECT and DOWN nodes")
    void rebuildRingExcludesNonUpNodes() {
        NodeInfo n1 = new NodeInfo("node-1", "localhost", 8081); n1.markUp();
        when(clusterRegistry.findByStatus(NodeStatus.UP)).thenReturn(List.of(n1));

        ringManager.rebuildRing();

        assertThat(ringManager.getRing().size()).isEqualTo(1);
        assertThat(ringManager.getRing().getNodeForKey("any-key"))
                .hasValue("node-1");
    }

    @Test
    @DisplayName("rebuildRing() returns empty ring when no UP nodes")
    void rebuildRingWithNoUpNodes() {
        when(clusterRegistry.findByStatus(NodeStatus.UP)).thenReturn(List.of());

        ringManager.rebuildRing();

        assertThat(ringManager.getRing().size()).isEqualTo(0);
    }

    @Test
    @DisplayName("ring is replaced atomically on each rebuild")
    void ringIsReplacedOnRebuild() {
        NodeInfo n1 = new NodeInfo("node-1", "localhost", 8081); n1.markUp();
        when(clusterRegistry.findByStatus(NodeStatus.UP)).thenReturn(List.of(n1));
        ringManager.rebuildRing();
        ConsistentHashRing firstRing = ringManager.getRing();

        NodeInfo n2 = new NodeInfo("node-2", "localhost", 8082); n2.markUp();
        when(clusterRegistry.findByStatus(NodeStatus.UP)).thenReturn(List.of(n1, n2));
        ringManager.rebuildRing();
        ConsistentHashRing secondRing = ringManager.getRing();

        assertThat(firstRing).isNotSameAs(secondRing);
        assertThat(firstRing.size()).isEqualTo(1);
        assertThat(secondRing.size()).isEqualTo(2);
    }
}
