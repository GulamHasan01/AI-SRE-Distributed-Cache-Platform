package com.gateway.controller;

import com.gateway.cluster.model.NodeInfo;
import com.gateway.cluster.model.NodeStatus;
import com.gateway.routing.ConsistentHashRing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Gateway Service Controller & Hash Ring Tests")
class GatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("ConsistentHashRing should distribute virtual nodes uniformly and route keys correctly")
    void hashRingRoutingTest() {
        NodeInfo node1 = new NodeInfo("node-1", "localhost", 8081, NodeStatus.UP);
        NodeInfo node2 = new NodeInfo("node-2", "localhost", 8082, NodeStatus.UP);
        NodeInfo node3 = new NodeInfo("node-3", "localhost", 8083, NodeStatus.UP);

        ConsistentHashRing ring = new ConsistentHashRing(Arrays.asList(node1, node2, node3), 10);

        assertThat(ring.getRingMap()).hasSize(30);

        String key = "test-user-cache-key-12345";
        String owner = ring.getNodeForKey(key).orElse(null);
        assertThat(owner).isNotNull();
        assertThat(owner).isIn("node-1", "node-2", "node-3");

        for (int i = 0; i < 5; i++) {
            assertThat(ring.getNodeForKey(key).orElse(null)).isEqualTo(owner);
        }
    }

    @Test
    @DisplayName("GET /api/v1/gateway/cluster/ring should return ring layout")
    void getClusterRingLayoutTest() throws Exception {
        mockMvc.perform(get("/api/v1/gateway/cluster/ring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Virtual nodes ring retrieved successfully"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/gateway/cluster/hash-key should resolve hash route details")
    void getHashKeyRoutingTest() throws Exception {
        mockMvc.perform(get("/api/v1/gateway/cluster/hash-key?key=test-key-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value("test-key-abc"))
                .andExpect(jsonPath("$.data.hash").exists())
                .andExpect(jsonPath("$.data.hashHex").exists())
                .andExpect(jsonPath("$.data.ownerNodeId").exists())
                .andExpect(jsonPath("$.data.replicas").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/gateway/cluster/health-report should return cluster diagnostic metrics")
    void getClusterHealthReportTest() throws Exception {
        mockMvc.perform(get("/api/v1/gateway/cluster/health-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cluster health report generated successfully"))
                .andExpect(jsonPath("$.data.totalNodes").exists())
                .andExpect(jsonPath("$.data.activeUpNodes").exists())
                .andExpect(jsonPath("$.data.virtualNodesInRing").exists())
                .andExpect(jsonPath("$.data.configuredBackendUrls").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/gateway/cluster/metrics should return aggregated cluster metrics")
    void getClusterMetricsTest() throws Exception {
        mockMvc.perform(get("/api/v1/gateway/cluster/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cluster metrics aggregated successfully"))
                .andExpect(jsonPath("$.data.clusterTotalKeys").exists())
                .andExpect(jsonPath("$.data.clusterTotalHits").exists())
                .andExpect(jsonPath("$.data.clusterTotalMisses").exists())
                .andExpect(jsonPath("$.data.clusterHitRatio").exists())
                .andExpect(jsonPath("$.data.perNodeStats").isArray());
    }

    @Test
    @DisplayName("PUT /api/v1/gateway/cluster/eviction-policy should return bad gateway when backing nodes are offline")
    void updateEvictionPolicyTest() throws Exception {
        mockMvc.perform(put("/api/v1/gateway/cluster/eviction-policy?policy=FIFO"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Failed to switch eviction policy on all cluster nodes"))
                .andExpect(jsonPath("$.data.policy").value("FIFO"));
    }

    @Test
    @DisplayName("POST /api/v1/gateway/cluster/config/nodes should dynamically add node URL to cluster layout")
    void addConfiguredNodeTest() throws Exception {
        mockMvc.perform(post("/api/v1/gateway/cluster/config/nodes?url=http://localhost:8084"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Node added to cluster layout successfully"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("DELETE /api/v1/gateway/cluster/config/nodes should dynamically remove node URL from cluster layout")
    void removeConfiguredNodeTest() throws Exception {
        mockMvc.perform(post("/api/v1/gateway/cluster/config/nodes?url=http://localhost:8085"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/gateway/cluster/config/nodes?url=http://localhost:8085"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Node removed from cluster layout successfully"));
    }
}
