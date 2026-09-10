package com.cache.cluster.startup;

import com.cache.cluster.routing.ClusterRingManager;
import com.cache.cluster.service.ClusterRegistryService;
import com.cache.config.CacheProperties;
import com.cache.dto.request.NodeRegistrationRequest;
import com.cache.dto.response.NodeInfoResponse;
import com.cache.cluster.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NodeStartupRegistrar")
class NodeStartupRegistrarTest {

    @Mock private ClusterRegistryService clusterRegistryService;
    @Mock private CacheProperties cacheProperties;
    @Mock private CacheProperties.NodeProperties nodeProps;
    @Mock private CacheProperties.ClusterProperties clusterProps;
    @Mock private ClusterRingManager clusterRingManager;

    private NodeStartupRegistrar registrar;

    private static final String SELF_ID   = "node-1";
    private static final String SELF_HOST = "localhost";
    private static final int    SELF_PORT = 8081;

    private NodeInfoResponse stubResponse() {
        return new NodeInfoResponse(
                SELF_ID, SELF_HOST, SELF_PORT,
                NodeStatus.STARTING,
                "http://localhost:8081",
                Instant.now(), Instant.now(), false);
    }

    @BeforeEach
    void setUp() {
        when(cacheProperties.getNode()).thenReturn(nodeProps);
        when(nodeProps.getId()).thenReturn(SELF_ID);
        when(nodeProps.getHost()).thenReturn(SELF_HOST);
        when(nodeProps.getPort()).thenReturn(SELF_PORT);

        registrar = new NodeStartupRegistrar(clusterRegistryService, cacheProperties, clusterRingManager);
    }

    @Nested
    @DisplayName("Happy-path startup sequence")
    class HappyPathTests {

        @BeforeEach
        void setUpCluster() {
            when(cacheProperties.getCluster()).thenReturn(clusterProps);
            when(clusterProps.getPeers()).thenReturn(List.of());
        }

        @Test
        @DisplayName("registers self with correct nodeId, host, and port")
        void registersSelf() throws Exception {
            when(clusterRegistryService.register(any())).thenReturn(stubResponse());

            registrar.run(new DefaultApplicationArguments());

            ArgumentCaptor<NodeRegistrationRequest> captor =
                    ArgumentCaptor.forClass(NodeRegistrationRequest.class);
            verify(clusterRegistryService).register(captor.capture());

            NodeRegistrationRequest req = captor.getValue();
            assertThat(req.nodeId()).isEqualTo(SELF_ID);
            assertThat(req.host()).isEqualTo(SELF_HOST);
            assertThat(req.port()).isEqualTo(SELF_PORT);
        }

        @Test
        @DisplayName("calls markNodeUp(selfId) after registration")
        void marksNodeUp() throws Exception {
            when(clusterRegistryService.register(any())).thenReturn(stubResponse());

            registrar.run(new DefaultApplicationArguments());

            verify(clusterRegistryService).markNodeUp(SELF_ID);
        }

        @Test
        @DisplayName("rebuilds the consistent hash ring after marking self UP")
        void rebuildsRing() throws Exception {
            when(clusterRegistryService.register(any())).thenReturn(stubResponse());

            registrar.run(new DefaultApplicationArguments());

            verify(clusterRingManager).rebuildRing();
        }

        @Test
        @DisplayName("full startup order: register → markUp → rebuildRing")
        void startupOrder() throws Exception {
            when(clusterRegistryService.register(any())).thenReturn(stubResponse());
            var order = inOrder(clusterRegistryService, clusterRingManager);

            registrar.run(new DefaultApplicationArguments());

            order.verify(clusterRegistryService).register(any());
            order.verify(clusterRegistryService).markNodeUp(SELF_ID);
            order.verify(clusterRingManager).rebuildRing();
        }
    }

    @Nested
    @DisplayName("Peer pre-registration")
    class PeerPreRegistrationTests {

        @BeforeEach
        void setUpCluster() {
            when(cacheProperties.getCluster()).thenReturn(clusterProps);
        }

        @Test
        @DisplayName("pre-registers valid peers listed in config")
        void preRegistersConfiguredPeers() throws Exception {
            when(clusterProps.getPeers()).thenReturn(List.of("localhost:8082", "localhost:8083"));
            when(clusterRegistryService.register(any())).thenReturn(stubResponse());

            registrar.run(new DefaultApplicationArguments());

            verify(clusterRegistryService, times(3)).register(any());
        }

        @Test
        @DisplayName("derives peer node ID as 'node-N' for ports 8081–8090")
        void derivesPeerNodeId() throws Exception {
            when(clusterProps.getPeers()).thenReturn(List.of("localhost:8082"));
            when(clusterRegistryService.register(any())).thenReturn(stubResponse());

            registrar.run(new DefaultApplicationArguments());

            ArgumentCaptor<NodeRegistrationRequest> captor =
                    ArgumentCaptor.forClass(NodeRegistrationRequest.class);
            verify(clusterRegistryService, times(2)).register(captor.capture());

            NodeRegistrationRequest peerReq = captor.getAllValues().get(1);
            assertThat(peerReq.nodeId()).isEqualTo("node-2");
            assertThat(peerReq.host()).isEqualTo("localhost");
            assertThat(peerReq.port()).isEqualTo(8082);
        }

        @Test
        @DisplayName("skips peer entry whose ID equals self")
        void skipsPeerThatIsSelf() throws Exception {
            when(clusterProps.getPeers()).thenReturn(List.of("localhost:8081"));
            when(clusterRegistryService.register(any())).thenReturn(stubResponse());

            registrar.run(new DefaultApplicationArguments());

            verify(clusterRegistryService, times(1)).register(any());
        }

        @Test
        @DisplayName("continues startup when one peer entry is malformed")
        void continuesWhenPeerMalformed() throws Exception {
            when(clusterProps.getPeers()).thenReturn(List.of("INVALID", "localhost:8082"));
            when(clusterRegistryService.register(any())).thenReturn(stubResponse());

            assertThatCode(() -> registrar.run(new DefaultApplicationArguments()))
                    .doesNotThrowAnyException();

            verify(clusterRegistryService, times(2)).register(any());
        }
    }

    @Nested
    @DisplayName("Error resilience")
    class ErrorResilienceTests {

        @Test
        @DisplayName("does NOT throw when register() throws — node keeps starting")
        void doesNotCrashWhenRegistrationFails() {
            when(clusterRegistryService.register(any()))
                    .thenThrow(new RuntimeException("Registry unavailable"));

            assertThatCode(() -> registrar.run(new DefaultApplicationArguments()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("does NOT call markNodeUp when register() throws")
        void doesNotMarkUpWhenRegistrationFails() throws Exception {
            when(clusterRegistryService.register(any()))
                    .thenThrow(new RuntimeException("Registry unavailable"));

            registrar.run(new DefaultApplicationArguments());

            verify(clusterRegistryService, never()).markNodeUp(any());
        }

        @Test
        @DisplayName("does NOT rebuild ring when registration fails")
        void doesNotRebuildRingWhenRegistrationFails() throws Exception {
            when(clusterRegistryService.register(any()))
                    .thenThrow(new RuntimeException("Registry unavailable"));

            registrar.run(new DefaultApplicationArguments());

            verify(clusterRingManager, never()).rebuildRing();
        }
    }
}
