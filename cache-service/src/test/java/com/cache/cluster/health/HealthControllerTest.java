package com.cache.cluster.health;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.config.CacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthController")
class HealthControllerTest {

    @Mock private ClusterRegistry clusterRegistry;
    @Mock private CacheProperties cacheProperties;
    @Mock private CacheProperties.NodeProperties nodeProps;
    @Mock private CacheProperties.ClusterProperties clusterPropsMock;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SELF_ID      = "node-1";
    private static final long   SUSPECT_MS   = 10_000L;
    private static final long   DOWN_MS      = 20_000L;
    private static final long   HEARTBEAT_MS = 5_000L;

    @BeforeEach
    void setUp() {
        when(cacheProperties.getNode()).thenReturn(nodeProps);
        when(nodeProps.getId()).thenReturn(SELF_ID);

        HealthController controller = new HealthController(clusterRegistry, cacheProperties);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    @DisplayName("GET /ping")
    class PingTests {

        @Test
        @DisplayName("returns HTTP 200")
        void returns200() throws Exception {
            mockMvc.perform(get("/api/v1/cluster/health/ping"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("response body contains success=true")
        void responseBodyHasSuccessTrue() throws Exception {
            mockMvc.perform(get("/api/v1/cluster/health/ping"))
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("response payload contains nodeId of this node")
        void payloadContainsNodeId() throws Exception {
            mockMvc.perform(get("/api/v1/cluster/health/ping"))
                    .andExpect(jsonPath("$.data.nodeId").value(SELF_ID));
        }

        @Test
        @DisplayName("response payload contains status=UP")
        void payloadContainsStatusUp() throws Exception {
            mockMvc.perform(get("/api/v1/cluster/health/ping"))
                    .andExpect(jsonPath("$.data.status").value("UP"));
        }

        @Test
        @DisplayName("response payload contains a timestamp")
        void payloadContainsTimestamp() throws Exception {
            mockMvc.perform(get("/api/v1/cluster/health/ping"))
                    .andExpect(jsonPath("$.data.timestamp").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("GET /status")
    class StatusTests {

        @BeforeEach
        void setUpClusterProps() {
            when(cacheProperties.getCluster()).thenReturn(clusterPropsMock);
            when(clusterPropsMock.getSuspectThresholdMs()).thenReturn(SUSPECT_MS);
            when(clusterPropsMock.getDownThresholdMs()).thenReturn(DOWN_MS);
            when(clusterPropsMock.getHeartbeatIntervalMs()).thenReturn(HEARTBEAT_MS);
        }

        @Test
        @DisplayName("returns HTTP 200")
        void returns200() throws Exception {
            when(clusterRegistry.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/cluster/health/status"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("response body contains success=true")
        void responseBodyHasSuccessTrue() throws Exception {
            when(clusterRegistry.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/cluster/health/status"))
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("response includes suspectThresholdMs and downThresholdMs")
        void responseIncludesThresholds() throws Exception {
            when(clusterRegistry.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/cluster/health/status"))
                    .andExpect(jsonPath("$.data.suspectThresholdMs").value(SUSPECT_MS))
                    .andExpect(jsonPath("$.data.downThresholdMs").value(DOWN_MS))
                    .andExpect(jsonPath("$.data.heartbeatIntervalMs").value(HEARTBEAT_MS));
        }

        @Test
        @DisplayName("reports correct totalNodes count")
        void reportsTotalNodes() throws Exception {
            NodeInfo n1 = new NodeInfo("node-1", "localhost", 8081);
            NodeInfo n2 = new NodeInfo("node-2", "localhost", 8082);
            n1.markUp(); n2.markUp();
            when(clusterRegistry.findAll()).thenReturn(List.of(n1, n2));

            mockMvc.perform(get("/api/v1/cluster/health/status"))
                    .andExpect(jsonPath("$.data.totalNodes").value(2));
        }

        @Test
        @DisplayName("upCount reflects only UP nodes")
        void upCountIsCorrect() throws Exception {
            NodeInfo up      = new NodeInfo("node-1", "localhost", 8081); up.markUp();
            NodeInfo suspect = new NodeInfo("node-2", "localhost", 8082); suspect.setStatus(NodeStatus.SUSPECT);
            NodeInfo down    = new NodeInfo("node-3", "localhost", 8083); down.setStatus(NodeStatus.DOWN);

            when(clusterRegistry.findAll()).thenReturn(List.of(up, suspect, down));

            mockMvc.perform(get("/api/v1/cluster/health/status"))
                    .andExpect(jsonPath("$.data.upCount").value(1))
                    .andExpect(jsonPath("$.data.suspectCount").value(1))
                    .andExpect(jsonPath("$.data.downCount").value(1));
        }

        @Test
        @DisplayName("self node has isSelf=true and msUntilSuspect=N/A")
        void selfNodeHasCorrectFlags() throws Exception {
            NodeInfo self = new NodeInfo(SELF_ID, "localhost", 8081);
            self.markUp();
            when(clusterRegistry.findAll()).thenReturn(List.of(self));

            mockMvc.perform(get("/api/v1/cluster/health/status"))
                    .andExpect(jsonPath("$.data.nodes." + SELF_ID + ".isSelf").value(true))
                    .andExpect(jsonPath("$.data.nodes." + SELF_ID + ".msUntilSuspect").value("N/A"))
                    .andExpect(jsonPath("$.data.nodes." + SELF_ID + ".msUntilDown").value("N/A"));
        }

        @Test
        @DisplayName("DOWN node shows msUntilSuspect=N/A")
        void downNodeHasNAForThresholds() throws Exception {
            NodeInfo down = new NodeInfo("node-2", "localhost", 8082);
            down.setStatus(NodeStatus.DOWN);
            when(clusterRegistry.findAll()).thenReturn(List.of(down));

            mockMvc.perform(get("/api/v1/cluster/health/status"))
                    .andExpect(jsonPath("$.data.nodes.node-2.msUntilSuspect").value("N/A"))
                    .andExpect(jsonPath("$.data.nodes.node-2.msUntilDown").value("N/A"));
        }

        @Test
        @DisplayName("UP peer node shows numeric msUntilSuspect value")
        void upPeerHasNumericThresholds() throws Exception {
            NodeInfo peer = new NodeInfo("node-2", "localhost", 8082);
            peer.markUp();
            when(clusterRegistry.findAll()).thenReturn(List.of(peer));

            mockMvc.perform(get("/api/v1/cluster/health/status"))
                    .andExpect(jsonPath("$.data.nodes.node-2.msUntilSuspect").isNumber())
                    .andExpect(jsonPath("$.data.nodes.node-2.msUntilDown").isNumber());
        }

        @Test
        @DisplayName("empty cluster returns totalNodes=0")
        void emptyCluster() throws Exception {
            when(clusterRegistry.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/cluster/health/status"))
                    .andExpect(jsonPath("$.data.totalNodes").value(0))
                    .andExpect(jsonPath("$.data.upCount").value(0));
        }
    }
}
