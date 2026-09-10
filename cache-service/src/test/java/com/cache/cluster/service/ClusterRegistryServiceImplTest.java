package com.cache.cluster.service;

import com.cache.cluster.exception.NodeNotFoundException;
import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.cluster.routing.ClusterRingManager;
import com.cache.cluster.service.impl.ClusterRegistryServiceImpl;
import com.cache.dto.request.NodeRegistrationRequest;
import com.cache.dto.response.ClusterStatusResponse;
import com.cache.dto.response.NodeInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClusterRegistryService Unit Tests")
class ClusterRegistryServiceImplTest {

    @Mock
    private ClusterRegistry clusterRegistry;

    @Mock
    private ClusterRingManager clusterRingManager;

    private ClusterRegistryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClusterRegistryServiceImpl(clusterRegistry, clusterRingManager);
    }

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("should register node and return NodeInfoResponse with STARTING status")
        void shouldRegisterAndReturnResponse() {
            NodeRegistrationRequest request = new NodeRegistrationRequest("node-1", "localhost", 8081);
            doNothing().when(clusterRegistry).register(any(NodeInfo.class));

            NodeInfoResponse response = service.register(request);

            assertThat(response.nodeId()).isEqualTo("node-1");
            assertThat(response.host()).isEqualTo("localhost");
            assertThat(response.port()).isEqualTo(8081);
            assertThat(response.status()).isEqualTo(NodeStatus.STARTING);
            assertThat(response.baseUrl()).isEqualTo("http://localhost:8081");
            verify(clusterRegistry).register(any(NodeInfo.class));
        }
    }

    @Nested
    @DisplayName("deregister()")
    class DeregisterTests {

        @Test
        @DisplayName("should deregister existing node and rebuild ring")
        void shouldDeregisterExistingNode() {
            when(clusterRegistry.deregister("node-1")).thenReturn(true);

            assertThatCode(() -> service.deregister("node-1")).doesNotThrowAnyException();
            verify(clusterRegistry).deregister("node-1");
            verify(clusterRingManager).rebuildRing();
        }

        @Test
        @DisplayName("should throw NodeNotFoundException when node not found")
        void shouldThrowWhenNodeNotFound() {
            when(clusterRegistry.deregister("ghost")).thenReturn(false);

            assertThatThrownBy(() -> service.deregister("ghost"))
                    .isInstanceOf(NodeNotFoundException.class)
                    .hasMessageContaining("ghost");
            verify(clusterRingManager, never()).rebuildRing();
        }
    }

    @Nested
    @DisplayName("getNode()")
    class GetNodeTests {

        @Test
        @DisplayName("should return NodeInfoResponse when node exists")
        void shouldReturnNodeWhenExists() {
            NodeInfo node = new NodeInfo("node-1", "localhost", 8081);
            node.markUp();
            when(clusterRegistry.findById("node-1")).thenReturn(Optional.of(node));

            NodeInfoResponse response = service.getNode("node-1");

            assertThat(response.nodeId()).isEqualTo("node-1");
            assertThat(response.status()).isEqualTo(NodeStatus.UP);
        }

        @Test
        @DisplayName("should throw NodeNotFoundException when node not found")
        void shouldThrowForMissingNode() {
            when(clusterRegistry.findById("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getNode("ghost"))
                    .isInstanceOf(NodeNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getClusterStatus()")
    class ClusterStatusTests {

        @Test
        @DisplayName("should return healthy=false when cluster is empty")
        void shouldReturnUnhealthyWhenEmpty() {
            when(clusterRegistry.findAll()).thenReturn(List.of());

            ClusterStatusResponse status = service.getClusterStatus();

            assertThat(status.totalNodes()).isEqualTo(0);
            assertThat(status.clusterHealthy()).isFalse();
        }

        @Test
        @DisplayName("should return healthy=true when all nodes are UP")
        void shouldReturnHealthyWhenAllNodesUp() {
            NodeInfo n1 = new NodeInfo("n1", "h1", 1); n1.markUp();
            NodeInfo n2 = new NodeInfo("n2", "h2", 2); n2.markUp();
            when(clusterRegistry.findAll()).thenReturn(List.of(n1, n2));

            ClusterStatusResponse status = service.getClusterStatus();

            assertThat(status.totalNodes()).isEqualTo(2);
            assertThat(status.upCount()).isEqualTo(2);
            assertThat(status.downCount()).isEqualTo(0);
            assertThat(status.clusterHealthy()).isTrue();
        }

        @Test
        @DisplayName("should return healthy=false when any node is not UP")
        void shouldReturnUnhealthyWhenAnyNodeDown() {
            NodeInfo n1 = new NodeInfo("n1", "h1", 1); n1.markUp();
            NodeInfo n2 = new NodeInfo("n2", "h2", 2); n2.setStatus(NodeStatus.DOWN);
            when(clusterRegistry.findAll()).thenReturn(List.of(n1, n2));

            ClusterStatusResponse status = service.getClusterStatus();

            assertThat(status.upCount()).isEqualTo(1);
            assertThat(status.downCount()).isEqualTo(1);
            assertThat(status.clusterHealthy()).isFalse();
        }

        @Test
        @DisplayName("should correctly count nodes in each status")
        void shouldCountNodesByStatus() {
            NodeInfo n1 = new NodeInfo("n1", "h1", 1); n1.markUp();
            NodeInfo n2 = new NodeInfo("n2", "h2", 2);
            NodeInfo n3 = new NodeInfo("n3", "h3", 3); n3.setStatus(NodeStatus.SUSPECT);
            NodeInfo n4 = new NodeInfo("n4", "h4", 4); n4.setStatus(NodeStatus.DOWN);
            when(clusterRegistry.findAll()).thenReturn(List.of(n1, n2, n3, n4));

            ClusterStatusResponse status = service.getClusterStatus();

            assertThat(status.totalNodes()).isEqualTo(4);
            assertThat(status.upCount()).isEqualTo(1);
            assertThat(status.startingCount()).isEqualTo(1);
            assertThat(status.suspectCount()).isEqualTo(1);
            assertThat(status.downCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("markNodeUp()")
    class MarkNodeUpTests {

        @Test
        @DisplayName("should mark a STARTING node as UP and rebuild ring")
        void shouldMarkNodeUp() {
            NodeInfo node = new NodeInfo("node-1", "h", 1);
            when(clusterRegistry.findById("node-1")).thenReturn(Optional.of(node));

            service.markNodeUp("node-1");

            assertThat(node.getStatus()).isEqualTo(NodeStatus.UP);
            verify(clusterRingManager).rebuildRing();
        }

        @Test
        @DisplayName("should throw NodeNotFoundException when marking unknown node UP")
        void shouldThrowForMissingNode() {
            when(clusterRegistry.findById("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markNodeUp("ghost"))
                    .isInstanceOf(NodeNotFoundException.class);
            verify(clusterRingManager, never()).rebuildRing();
        }
    }

    @Nested
    @DisplayName("recordHeartbeat() — Phase 7")
    class RecordHeartbeatTests {

        @Test
        @DisplayName("should update lastHeartbeatAt and NOT rebuild ring when node stays UP")
        void shouldRecordHeartbeatWithoutRingRebuildWhenAlreadyUp() {
            NodeInfo node = new NodeInfo("node-2", "h", 2);
            node.markUp();
            when(clusterRegistry.findById("node-2")).thenReturn(Optional.of(node));

            service.recordHeartbeat("node-2");

            assertThat(node.getStatus()).isEqualTo(NodeStatus.UP);
            verify(clusterRingManager, never()).rebuildRing();
        }

        @Test
        @DisplayName("should heal SUSPECT → UP and rebuild ring on heartbeat")
        void shouldHealSuspectNodeAndRebuildRing() {
            NodeInfo node = new NodeInfo("node-2", "h", 2);
            node.setStatus(NodeStatus.SUSPECT);
            when(clusterRegistry.findById("node-2")).thenReturn(Optional.of(node));

            service.recordHeartbeat("node-2");

            assertThat(node.getStatus()).isEqualTo(NodeStatus.UP);
            verify(clusterRingManager).rebuildRing();
        }

        @Test
        @DisplayName("should throw NodeNotFoundException for unknown node")
        void shouldThrowForUnknownNode() {
            when(clusterRegistry.findById("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recordHeartbeat("ghost"))
                    .isInstanceOf(NodeNotFoundException.class);
        }
    }
}
