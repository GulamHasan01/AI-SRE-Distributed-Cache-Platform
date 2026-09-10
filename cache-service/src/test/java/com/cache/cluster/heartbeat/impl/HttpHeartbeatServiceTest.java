package com.cache.cluster.heartbeat.impl;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.cluster.service.ClusterRegistryService;
import com.cache.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HttpHeartbeatService")
class HttpHeartbeatServiceTest {

    @Mock private ClusterRegistry clusterRegistry;
    @Mock private ClusterRegistryService clusterRegistryService;
    @Mock private CacheProperties cacheProperties;
    @Mock private CacheProperties.NodeProperties nodeProps;

    private HttpHeartbeatService heartbeatService;
    private MockRestServiceServer mockServer;

    private static final String SELF_ID  = "node-1";
    private static final String PEER_ID  = "node-2";
    private static final String PEER_URL = "http://localhost:8082";
    private static final String PING_URL = PEER_URL + "/api/v1/cluster/health/ping";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient heartbeatClient = builder.build();

        heartbeatService = new HttpHeartbeatService(
                clusterRegistry, clusterRegistryService, cacheProperties, heartbeatClient);
    }

    @Nested
    @DisplayName("pingNode()")
    class PingNodeTests {

        @Test
        @DisplayName("returns true when peer responds with HTTP 200")
        void returnsTrueOnSuccess() {
            NodeInfo peer = new NodeInfo(PEER_ID, "localhost", 8082);
            mockServer.expect(requestTo(PING_URL))
                    .andExpect(method(org.springframework.http.HttpMethod.GET))
                    .andRespond(withSuccess());

            boolean result = heartbeatService.pingNode(peer);

            assertThat(result).isTrue();
            mockServer.verify();
        }

        @Test
        @DisplayName("returns false when peer responds with HTTP 500")
        void returnsFalseOnServerError() {
            NodeInfo peer = new NodeInfo(PEER_ID, "localhost", 8082);
            mockServer.expect(requestTo(PING_URL))
                    .andRespond(withServerError());

            boolean result = heartbeatService.pingNode(peer);

            assertThat(result).isFalse();
            mockServer.verify();
        }

        @Test
        @DisplayName("returns false when peer responds with HTTP 503 Service Unavailable")
        void returnsFalseOnServiceUnavailable() {
            NodeInfo peer = new NodeInfo(PEER_ID, "localhost", 8082);
            mockServer.expect(requestTo(PING_URL))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

            boolean result = heartbeatService.pingNode(peer);

            assertThat(result).isFalse();
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("emitHeartbeats()")
    class EmitHeartbeatsTests {

        @BeforeEach
        void setUpSelfId() {
            when(cacheProperties.getNode()).thenReturn(nodeProps);
            when(nodeProps.getId()).thenReturn(SELF_ID);
        }

        @Test
        @DisplayName("skips self — never pings own node ID")
        void skipsSelf() {
            NodeInfo self = new NodeInfo(SELF_ID, "localhost", 8081);
            self.markUp();

            when(clusterRegistry.findAll()).thenReturn(List.of(self));

            heartbeatService.emitHeartbeats();

            mockServer.verify();
            verifyNoInteractions(clusterRegistryService);
        }

        @Test
        @DisplayName("calls recordHeartbeat() on registry when peer responds 200")
        void recordsHeartbeatOnSuccess() {
            NodeInfo peer = new NodeInfo(PEER_ID, "localhost", 8082);
            peer.markUp();

            mockServer.expect(requestTo(PING_URL))
                    .andRespond(withSuccess());

            when(clusterRegistry.findAll()).thenReturn(List.of(peer));

            heartbeatService.emitHeartbeats();

            verify(clusterRegistryService).recordHeartbeat(PEER_ID);
            mockServer.verify();
        }

        @Test
        @DisplayName("does NOT call recordHeartbeat() when peer ping fails")
        void doesNotRecordHeartbeatOnFailure() {
            NodeInfo peer = new NodeInfo(PEER_ID, "localhost", 8082);
            peer.markUp();

            mockServer.expect(requestTo(PING_URL))
                    .andRespond(withServerError());

            when(clusterRegistry.findAll()).thenReturn(List.of(peer));

            heartbeatService.emitHeartbeats();

            verifyNoInteractions(clusterRegistryService);
            mockServer.verify();
        }

        @Test
        @DisplayName("pings all peers except self in a multi-node cluster")
        void pingsAllPeersExceptSelf() {
            NodeInfo self  = new NodeInfo(SELF_ID, "localhost", 8081);
            NodeInfo peer2 = new NodeInfo("node-2", "localhost", 8082);
            NodeInfo peer3 = new NodeInfo("node-3", "localhost", 8083);

            self.markUp(); peer2.markUp(); peer3.markUp();

            mockServer.expect(requestTo("http://localhost:8082/api/v1/cluster/health/ping"))
                    .andRespond(withSuccess());
            mockServer.expect(requestTo("http://localhost:8083/api/v1/cluster/health/ping"))
                    .andRespond(withSuccess());

            when(clusterRegistry.findAll()).thenReturn(List.of(self, peer2, peer3));

            heartbeatService.emitHeartbeats();

            verify(clusterRegistryService).recordHeartbeat("node-2");
            verify(clusterRegistryService).recordHeartbeat("node-3");
            verifyNoMoreInteractions(clusterRegistryService);
            mockServer.verify();
        }

        @Test
        @DisplayName("continues pinging remaining peers even when one ping throws")
        void continuesPingingAfterOneFailure() {
            NodeInfo peer2 = new NodeInfo("node-2", "localhost", 8082);
            NodeInfo peer3 = new NodeInfo("node-3", "localhost", 8083);
            peer2.markUp(); peer3.markUp();

            mockServer.expect(requestTo("http://localhost:8082/api/v1/cluster/health/ping"))
                    .andRespond(withServerError());
            mockServer.expect(requestTo("http://localhost:8083/api/v1/cluster/health/ping"))
                    .andRespond(withSuccess());

            when(clusterRegistry.findAll()).thenReturn(List.of(peer2, peer3));

            heartbeatService.emitHeartbeats();

            verify(clusterRegistryService, never()).recordHeartbeat("node-2");
            verify(clusterRegistryService).recordHeartbeat("node-3");
            mockServer.verify();
        }
    }
}
