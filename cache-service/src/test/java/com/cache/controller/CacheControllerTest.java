package com.cache.controller;

import com.cache.cluster.exception.NodeCommunicationException;
import com.cache.cluster.forwarding.RequestForwardingService;
import com.cache.cluster.routing.KeyRoutingService;
import com.cache.config.CacheProperties;
import com.cache.dto.request.CachePutRequest;
import com.cache.dto.response.CacheEntryResponse;
import com.cache.dto.response.CacheStatsResponse;
import com.cache.exception.CacheKeyNotFoundException;
import com.cache.service.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CacheController.class)
@DisplayName("CacheController Web Layer Tests (Phase 4B)")
class CacheControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CacheService cacheService;

    @MockBean
    private RequestForwardingService forwardingService;

    @MockBean
    private KeyRoutingService keyRoutingService;

    @MockBean
    private CacheProperties cacheProperties;

    @MockBean
    private com.cache.cluster.replication.ReplicationService replicationService;

    private static final String SELF_NODE_ID   = "node-1";
    private static final String REMOTE_NODE_ID = "node-2";

    private static final CacheEntryResponse SAMPLE_RESPONSE =
            new CacheEntryResponse("user:1001", "{\"name\":\"Alice\"}", Instant.now(), 0L, -1L, -1L, false);

    @BeforeEach
    void setUpNodeId() {
        CacheProperties.NodeProperties nodeProps = new CacheProperties.NodeProperties();
        nodeProps.setId(SELF_NODE_ID);
        when(cacheProperties.getNode()).thenReturn(nodeProps);
    }

    @Nested
    @DisplayName("PUT /api/v1/cache")
    class PutEndpointTests {

        @Test
        @DisplayName("hash routes to self -> local handling")
        void shouldHandleLocallyWhenHashRoutesToSelf() throws Exception {
            CachePutRequest request = new CachePutRequest("user:1001", "{\"name\":\"Alice\"}", null);
            when(keyRoutingService.getOwnerNodeId("user:1001")).thenReturn(SELF_NODE_ID);
            when(cacheService.put(any())).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(put("/api/v1/cache")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.key").value("user:1001"));

            verify(cacheService).put(any());
            verifyNoInteractions(forwardingService);
        }

        @Test
        @DisplayName("hash routes to remote -> forward PUT")
        void shouldForwardPutWhenHashRoutesToRemote() throws Exception {
            CachePutRequest request = new CachePutRequest("user:1001", "{\"name\":\"Alice\"}", null);
            when(keyRoutingService.getOwnerNodeId("user:1001")).thenReturn(REMOTE_NODE_ID);
            when(forwardingService.forwardPut(eq(REMOTE_NODE_ID), any())).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(put("/api/v1/cache")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.key").value("user:1001"));

            verify(forwardingService).forwardPut(eq(REMOTE_NODE_ID), any());
            verifyNoInteractions(cacheService);
        }

        @Test
        @DisplayName("X-Forwarded-By present -> loop guard, always local")
        void shouldHandleLocallyWhenForwardedByPresent() throws Exception {
            CachePutRequest request = new CachePutRequest("user:1001", "{\"name\":\"Alice\"}", null);
            when(cacheService.put(any())).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(put("/api/v1/cache")
                            .header(CacheController.FORWARDED_BY_HEADER, REMOTE_NODE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));

            verify(cacheService).put(any());
            verifyNoInteractions(forwardingService);
            verify(keyRoutingService, never()).getOwnerNodeId(anyString());
        }

        @Test
        @DisplayName("X-Target-Node explicit override -> forward to named node")
        void shouldForwardToExplicitTargetNode() throws Exception {
            CachePutRequest request = new CachePutRequest("user:1001", "{\"name\":\"Alice\"}", null);
            when(forwardingService.forwardPut(eq(REMOTE_NODE_ID), any())).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(put("/api/v1/cache")
                            .header(CacheController.TARGET_NODE_HEADER, REMOTE_NODE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));

            verify(forwardingService).forwardPut(eq(REMOTE_NODE_ID), any());
            verifyNoInteractions(keyRoutingService);
        }

        @Test
        @DisplayName("502 when remote node unreachable")
        void shouldReturn502WhenNodeUnreachable() throws Exception {
            CachePutRequest request = new CachePutRequest("user:1001", "{\"name\":\"Alice\"}", null);
            when(keyRoutingService.getOwnerNodeId("user:1001")).thenReturn(REMOTE_NODE_ID);
            when(forwardingService.forwardPut(eq(REMOTE_NODE_ID), any()))
                    .thenThrow(new NodeCommunicationException(
                            REMOTE_NODE_ID, "http://node-2:8082/api/v1/cache", "Timeout"));

            mockMvc.perform(put("/api/v1/cache")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 when key is blank")
        void shouldReturn400WhenKeyBlank() throws Exception {
            mockMvc.perform(put("/api/v1/cache")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\":\"\",\"value\":\"v\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 when value is blank")
        void shouldReturn400WhenValueBlank() throws Exception {
            mockMvc.perform(put("/api/v1/cache")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\":\"k\",\"value\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("X-Replicated-From present -> write locally, no further forwarding or replication")
        void shouldWriteLocallyOnReplicationRequest() throws Exception {
            CachePutRequest request = new CachePutRequest("user:1001", "{\"name\":\"Alice\"}", null);
            when(cacheService.put(any())).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(put("/api/v1/cache")
                            .header(CacheController.REPLICATED_FROM_HEADER, REMOTE_NODE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));

            verify(cacheService).put(any());
            verifyNoInteractions(forwardingService);
            verifyNoInteractions(keyRoutingService);
        }

        @Test
        @DisplayName("routes to self -> write locally and trigger replication to backups")
        void shouldReplicateToBackupsWhenOwnerIsSelf() throws Exception {
            CachePutRequest request = new CachePutRequest("user:1001", "{\"name\":\"Alice\"}", null);
            when(keyRoutingService.getOwnerNodeId("user:1001")).thenReturn(SELF_NODE_ID);
            when(keyRoutingService.getReplicaNodeIds("user:1001")).thenReturn(List.of(REMOTE_NODE_ID));
            when(cacheService.put(any())).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(put("/api/v1/cache")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));

            verify(cacheService).put(any());
            verify(replicationService).replicatePutAsync(any(), eq(List.of(REMOTE_NODE_ID)), eq(SELF_NODE_ID));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/cache/{key}")
    class GetEndpointTests {

        @Test
        @DisplayName("hash routes to self -> local GET")
        void shouldReturn200OnCacheHit() throws Exception {
            when(keyRoutingService.getOwnerNodeId("user:1001")).thenReturn(SELF_NODE_ID);
            when(cacheService.get("user:1001")).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(get("/api/v1/cache/user:1001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.key").value("user:1001"));

            verify(cacheService).get("user:1001");
            verifyNoInteractions(forwardingService);
        }

        @Test
        @DisplayName("hash routes to remote -> forward GET")
        void shouldForwardGetWhenHashRoutesToRemote() throws Exception {
            when(keyRoutingService.getOwnerNodeId("user:1001")).thenReturn(REMOTE_NODE_ID);
            when(forwardingService.forwardGet(REMOTE_NODE_ID, "user:1001")).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(get("/api/v1/cache/user:1001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(forwardingService).forwardGet(REMOTE_NODE_ID, "user:1001");
            verifyNoInteractions(cacheService);
        }

        @Test
        @DisplayName("X-Forwarded-By present -> loop guard, local GET")
        void shouldHandleLocallyWhenForwardedByPresent() throws Exception {
            when(cacheService.get("user:1001")).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(get("/api/v1/cache/user:1001")
                            .header(CacheController.FORWARDED_BY_HEADER, REMOTE_NODE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(cacheService).get("user:1001");
            verifyNoInteractions(forwardingService);
            verifyNoInteractions(keyRoutingService);
        }

        @Test
        @DisplayName("404 on cache miss (local)")
        void shouldReturn404OnCacheMiss() throws Exception {
            when(keyRoutingService.getOwnerNodeId("ghost")).thenReturn(SELF_NODE_ID);
            when(cacheService.get("ghost")).thenThrow(new CacheKeyNotFoundException("ghost"));

            mockMvc.perform(get("/api/v1/cache/ghost"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("X-Replicated-From present -> read locally, no forwarding")
        void shouldReadLocallyOnReplicatedGet() throws Exception {
            when(cacheService.get("user:1001")).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(get("/api/v1/cache/user:1001")
                            .header(CacheController.REPLICATED_FROM_HEADER, REMOTE_NODE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.key").value("user:1001"));

            verify(cacheService).get("user:1001");
            verifyNoInteractions(forwardingService);
            verifyNoInteractions(keyRoutingService);
        }

        @Test
        @DisplayName("primary unreachable -> try failover replicas")
        void shouldFailoverToReplicasWhenPrimaryUnreachable() throws Exception {
            when(keyRoutingService.getOwnerNodeId("user:1001")).thenReturn(REMOTE_NODE_ID);
            when(keyRoutingService.getReplicaNodeIds("user:1001")).thenReturn(List.of("node-3"));

            when(forwardingService.forwardGet(REMOTE_NODE_ID, "user:1001"))
                    .thenThrow(new NodeCommunicationException(REMOTE_NODE_ID, "http://node-2/api", "Timeout"));
            when(forwardingService.forwardGet("node-3", "user:1001")).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(get("/api/v1/cache/user:1001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.key").value("user:1001"));

            verify(forwardingService).forwardGet(REMOTE_NODE_ID, "user:1001");
            verify(forwardingService).forwardGet("node-3", "user:1001");
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/cache/{key}")
    class DeleteEndpointTests {

        @Test
        @DisplayName("hash routes to self -> local DELETE")
        void shouldReturn200OnDelete() throws Exception {
            when(keyRoutingService.getOwnerNodeId("user:1001")).thenReturn(SELF_NODE_ID);
            doNothing().when(cacheService).delete("user:1001");

            mockMvc.perform(delete("/api/v1/cache/user:1001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(cacheService).delete("user:1001");
            verifyNoInteractions(forwardingService);
        }

        @Test
        @DisplayName("hash routes to remote -> forward DELETE")
        void shouldForwardDeleteWhenHashRoutesToRemote() throws Exception {
            when(keyRoutingService.getOwnerNodeId("user:1001")).thenReturn(REMOTE_NODE_ID);
            doNothing().when(forwardingService).forwardDelete(REMOTE_NODE_ID, "user:1001");

            mockMvc.perform(delete("/api/v1/cache/user:1001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(forwardingService).forwardDelete(REMOTE_NODE_ID, "user:1001");
            verifyNoInteractions(cacheService);
        }

        @Test
        @DisplayName("X-Forwarded-By present -> loop guard, local DELETE")
        void shouldHandleLocallyWhenForwardedByPresent() throws Exception {
            doNothing().when(cacheService).delete("user:1001");

            mockMvc.perform(delete("/api/v1/cache/user:1001")
                            .header(CacheController.FORWARDED_BY_HEADER, REMOTE_NODE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(cacheService).delete("user:1001");
            verifyNoInteractions(forwardingService);
            verify(keyRoutingService, never()).getOwnerNodeId(anyString());
        }

        @Test
        @DisplayName("404 when deleting non-existent key (local)")
        void shouldReturn404WhenKeyNotFound() throws Exception {
            when(keyRoutingService.getOwnerNodeId("ghost")).thenReturn(SELF_NODE_ID);
            doThrow(new CacheKeyNotFoundException("ghost")).when(cacheService).delete("ghost");

            mockMvc.perform(delete("/api/v1/cache/ghost"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("X-Replicated-From present -> delete locally, no further forwarding or replication")
        void shouldDeleteLocallyOnReplicatedRequest() throws Exception {
            doNothing().when(cacheService).delete("user:1001");

            mockMvc.perform(delete("/api/v1/cache/user:1001")
                            .header(CacheController.REPLICATED_FROM_HEADER, REMOTE_NODE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(cacheService).delete("user:1001");
            verifyNoInteractions(forwardingService);
            verifyNoInteractions(keyRoutingService);
        }

        @Test
        @DisplayName("routes to self -> delete locally and trigger replication of DELETE to backups")
        void shouldReplicateDeleteToBackupsWhenOwnerIsSelf() throws Exception {
            when(keyRoutingService.getOwnerNodeId("user:1001")).thenReturn(SELF_NODE_ID);
            when(keyRoutingService.getReplicaNodeIds("user:1001")).thenReturn(List.of(REMOTE_NODE_ID));
            doNothing().when(cacheService).delete("user:1001");

            mockMvc.perform(delete("/api/v1/cache/user:1001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(cacheService).delete("user:1001");
            verify(replicationService).replicateDeleteAsync(eq("user:1001"), eq(List.of(REMOTE_NODE_ID)), eq(SELF_NODE_ID));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/cache (clear all)")
    class ClearEndpointTests {

        @Test
        @DisplayName("200 with count of removed entries")
        void shouldReturn200WithRemovedCount() throws Exception {
            when(cacheService.clear()).thenReturn(42L);

            mockMvc.perform(delete("/api/v1/cache"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(42));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/cache/stats")
    class StatsEndpointTests {

        @Test
        @DisplayName("200 with stats payload")
        void shouldReturn200WithStats() throws Exception {
            CacheStatsResponse stats = new CacheStatsResponse("node-1", 50L, 1000, 5.0, 200L, 30L, 87.0, 0L, 0L, "LRU", com.cache.cluster.replication.ReplicationStats.empty());
            when(cacheService.getStats()).thenReturn(stats);

            mockMvc.perform(get("/api/v1/cache/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.nodeId").value("node-1"))
                    .andExpect(jsonPath("$.data.totalKeys").value(50))
                    .andExpect(jsonPath("$.data.hitRatio").value(87.0));
        }
    }
}