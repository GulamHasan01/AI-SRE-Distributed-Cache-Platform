package com.cache.cluster.forwarding.impl;

import com.cache.cluster.client.NodeCommunicationClient;
import com.cache.cluster.exception.NodeNotFoundException;
import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.registry.ClusterRegistry;
import com.cache.dto.request.CachePutRequest;
import com.cache.dto.response.CacheEntryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestForwardingServiceImpl Unit Tests")
class RequestForwardingServiceImplTest {

    @Mock
    private ClusterRegistry clusterRegistry;

    @Mock
    private NodeCommunicationClient communicationClient;

    private RequestForwardingServiceImpl service;
    private NodeInfo targetNode;

    @BeforeEach
    void setUp() {
        service = new RequestForwardingServiceImpl(clusterRegistry, communicationClient);
        targetNode = new NodeInfo("node-2", "localhost", 8082);
    }

    @Test
    @DisplayName("forwardGet should fetch node metadata and execute get call")
    void shouldForwardGet() {
        CacheEntryResponse mockResponse = new CacheEntryResponse(
                "k1", "v1", Instant.now(), 1L, -1L, -1L, false
        );
        when(clusterRegistry.findById("node-2")).thenReturn(Optional.of(targetNode));
        when(communicationClient.get(targetNode, "k1")).thenReturn(mockResponse);

        CacheEntryResponse response = service.forwardGet("node-2", "k1");

        assertThat(response).isEqualTo(mockResponse);
        verify(clusterRegistry).findById("node-2");
        verify(communicationClient).get(targetNode, "k1");
    }

    @Test
    @DisplayName("forwardGet should throw NodeNotFoundException when target node ID not in registry")
    void shouldThrowOnForwardGetWhenNodeNotFound() {
        when(clusterRegistry.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forwardGet("ghost", "k1"))
                .isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    @DisplayName("forwardPut should fetch node metadata and execute put call")
    void shouldForwardPut() {
        CachePutRequest putRequest = new CachePutRequest("k1", "v1", 60L);
        CacheEntryResponse mockResponse = new CacheEntryResponse(
                "k1", "v1", Instant.now(), 0L, 60L, 60L, false
        );
        when(clusterRegistry.findById("node-2")).thenReturn(Optional.of(targetNode));
        when(communicationClient.put(targetNode, putRequest)).thenReturn(mockResponse);

        CacheEntryResponse response = service.forwardPut("node-2", putRequest);

        assertThat(response).isEqualTo(mockResponse);
        verify(clusterRegistry).findById("node-2");
        verify(communicationClient).put(targetNode, putRequest);
    }

    @Test
    @DisplayName("forwardDelete should fetch node metadata and execute delete call")
    void shouldForwardDelete() {
        when(clusterRegistry.findById("node-2")).thenReturn(Optional.of(targetNode));
        doNothing().when(communicationClient).delete(targetNode, "k1");

        service.forwardDelete("node-2", "k1");

        verify(clusterRegistry).findById("node-2");
        verify(communicationClient).delete(targetNode, "k1");
    }
}
