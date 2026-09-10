package com.cache.cluster.health.impl;

import com.cache.cluster.health.FailureDetectorService;
import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.cluster.routing.ClusterRingManager;
import com.cache.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FailureDetectorServiceImpl")
class FailureDetectorServiceImplTest {

    @Mock private ClusterRegistry clusterRegistry;
    @Mock private ClusterRingManager clusterRingManager;
    @Mock private CacheProperties cacheProperties;
    @Mock private CacheProperties.ClusterProperties clusterProps;
    @Mock private CacheProperties.NodeProperties nodeProps;

    private FailureDetectorService detector;

    private static final String SELF_ID = "node-1";
    private static final long SUSPECT_MS = 10_000L;
    private static final long DOWN_MS    = 20_000L;

    @BeforeEach
    void setUp() {
        when(cacheProperties.getNode()).thenReturn(nodeProps);
        when(nodeProps.getId()).thenReturn(SELF_ID);
        when(cacheProperties.getCluster()).thenReturn(clusterProps);
        when(clusterProps.getSuspectThresholdMs()).thenReturn(SUSPECT_MS);
        when(clusterProps.getDownThresholdMs()).thenReturn(DOWN_MS);

        detector = new FailureDetectorServiceImpl(clusterRegistry, clusterRingManager, cacheProperties);
    }

    private void setHeartbeatAge(NodeInfo node, long ageMs) {
        try {
            var field = NodeInfo.class.getDeclaredField("lastHeartbeatAt");
            field.setAccessible(true);
            field.set(node, Instant.now().minusMillis(ageMs));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set heartbeat age via reflection", e);
        }
    }

    @Nested
    @DisplayName("Self node")
    class SelfNodeTests {

        @Test
        @DisplayName("self node is never marked DOWN even with stale heartbeat")
        void selfIsNeverMarkedDown() {
            NodeInfo self = new NodeInfo(SELF_ID, "localhost", 8081);
            self.markUp();
            setHeartbeatAge(self, DOWN_MS + 5000);

            when(clusterRegistry.findAll()).thenReturn(List.of(self));

            detector.runDetectionCycle();

            assertThat(self.getStatus()).isEqualTo(NodeStatus.UP);
            verify(clusterRingManager, never()).rebuildRing();
        }
    }

    @Nested
    @DisplayName("UP → SUSPECT transition")
    class SuspectTransitionTests {

        @Test
        @DisplayName("UP node transitions to SUSPECT when heartbeat age exceeds suspect threshold")
        void upNodeBecomeSuspect() {
            NodeInfo peer = new NodeInfo("node-2", "localhost", 8082);
            peer.markUp();
            setHeartbeatAge(peer, SUSPECT_MS + 1000);

            when(clusterRegistry.findAll()).thenReturn(List.of(peer));

            detector.runDetectionCycle();

            assertThat(peer.getStatus()).isEqualTo(NodeStatus.SUSPECT);
            verify(clusterRingManager).rebuildRing();
        }

        @Test
        @DisplayName("UP node stays UP when heartbeat is fresh")
        void upNodeStaysUpWithFreshHeartbeat() {
            NodeInfo peer = new NodeInfo("node-2", "localhost", 8082);
            peer.markUp();
            setHeartbeatAge(peer, 1000);

            when(clusterRegistry.findAll()).thenReturn(List.of(peer));

            detector.runDetectionCycle();

            assertThat(peer.getStatus()).isEqualTo(NodeStatus.UP);
            verify(clusterRingManager, never()).rebuildRing();
        }
    }

    @Nested
    @DisplayName("SUSPECT/UP → DOWN transition")
    class DownTransitionTests {

        @Test
        @DisplayName("SUSPECT node transitions to DOWN when heartbeat age exceeds down threshold")
        void suspectNodeBecomesDown() {
            NodeInfo peer = new NodeInfo("node-2", "localhost", 8082);
            peer.setStatus(NodeStatus.SUSPECT);
            setHeartbeatAge(peer, DOWN_MS + 1000);

            when(clusterRegistry.findAll()).thenReturn(List.of(peer));

            detector.runDetectionCycle();

            assertThat(peer.getStatus()).isEqualTo(NodeStatus.DOWN);
            verify(clusterRingManager).rebuildRing();
        }

        @Test
        @DisplayName("UP node goes directly to DOWN when heartbeat age exceeds down threshold")
        void upNodeGoesDirectlyToDown() {
            NodeInfo peer = new NodeInfo("node-2", "localhost", 8082);
            peer.markUp();
            setHeartbeatAge(peer, DOWN_MS + 5000);

            when(clusterRegistry.findAll()).thenReturn(List.of(peer));

            detector.runDetectionCycle();

            assertThat(peer.getStatus()).isEqualTo(NodeStatus.DOWN);
            verify(clusterRingManager).rebuildRing();
        }

        @Test
        @DisplayName("already-DOWN node is not re-transitioned")
        void alreadyDownNodeIsNotRetransitioned() {
            NodeInfo peer = new NodeInfo("node-2", "localhost", 8082);
            peer.setStatus(NodeStatus.DOWN);
            setHeartbeatAge(peer, DOWN_MS + 5000);

            when(clusterRegistry.findAll()).thenReturn(List.of(peer));

            detector.runDetectionCycle();

            assertThat(peer.getStatus()).isEqualTo(NodeStatus.DOWN);
            verify(clusterRingManager, never()).rebuildRing();
        }
    }

    @Nested
    @DisplayName("No ring rebuild when nothing changes")
    class NoRingRebuildTests {

        @Test
        @DisplayName("ring is not rebuilt when all peers have fresh heartbeats")
        void noRingRebuildWhenAllFresh() {
            NodeInfo p1 = new NodeInfo("node-2", "localhost", 8082); p1.markUp();
            NodeInfo p2 = new NodeInfo("node-3", "localhost", 8083); p2.markUp();
            setHeartbeatAge(p1, 500);
            setHeartbeatAge(p2, 500);

            when(clusterRegistry.findAll()).thenReturn(List.of(p1, p2));

            detector.runDetectionCycle();

            verify(clusterRingManager, never()).rebuildRing();
        }

        @Test
        @DisplayName("ring is rebuilt exactly once when multiple peers fail simultaneously")
        void ringRebuiltOnceForMultipleFailures() {
            NodeInfo p1 = new NodeInfo("node-2", "localhost", 8082); p1.markUp();
            NodeInfo p2 = new NodeInfo("node-3", "localhost", 8083); p2.markUp();
            setHeartbeatAge(p1, SUSPECT_MS + 1000);
            setHeartbeatAge(p2, SUSPECT_MS + 1000);

            when(clusterRegistry.findAll()).thenReturn(List.of(p1, p2));

            detector.runDetectionCycle();

            assertThat(p1.getStatus()).isEqualTo(NodeStatus.SUSPECT);
            assertThat(p2.getStatus()).isEqualTo(NodeStatus.SUSPECT);
            verify(clusterRingManager, times(1)).rebuildRing();
        }
    }
}
