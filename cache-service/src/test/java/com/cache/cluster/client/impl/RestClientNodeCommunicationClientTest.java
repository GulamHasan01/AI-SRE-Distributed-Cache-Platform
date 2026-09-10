package com.cache.cluster.client.impl;

import com.cache.cluster.exception.NodeCommunicationException;
import com.cache.cluster.model.NodeInfo;
import com.cache.dto.request.CachePutRequest;
import com.cache.dto.response.CacheEntryResponse;
import com.cache.exception.CacheCapacityExceededException;
import com.cache.exception.CacheKeyNotFoundException;
import com.cache.config.CacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("RestClientNodeCommunicationClient Unit Tests")
class RestClientNodeCommunicationClientTest {

    private RestClientNodeCommunicationClient client;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private NodeInfo targetNode;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        CacheProperties cacheProperties = org.mockito.Mockito.mock(CacheProperties.class);
        CacheProperties.NodeProperties nodeProps = new CacheProperties.NodeProperties();
        nodeProps.setId("node-1");
        org.mockito.Mockito.when(cacheProperties.getNode()).thenReturn(nodeProps);

        client = new RestClientNodeCommunicationClient(restClient, cacheProperties);
        targetNode = new NodeInfo("node-2", "localhost", 8082);
    }

    @Nested
    @DisplayName("get() Tests")
    class GetTests {

        @Test
        @DisplayName("should return CacheEntryResponse on 200 OK success")
        void shouldReturnResponseOnSuccess() throws Exception {
            CacheEntryResponse mockResponse = new CacheEntryResponse(
                    "k1", "v1", Instant.now(), 1L, -1L, -1L, false
            );
            String successBody = "{\"success\":true,\"message\":\"OK\",\"data\":" +
                    objectMapper.writeValueAsString(mockResponse) + "}";

            mockServer.expect(requestTo("http://localhost:8082/api/v1/cache/k1"))
                    .andExpect(method(org.springframework.http.HttpMethod.GET))
                    .andRespond(withSuccess(successBody, MediaType.APPLICATION_JSON));

            CacheEntryResponse result = client.get(targetNode, "k1");

            assertThat(result.key()).isEqualTo("k1");
            assertThat(result.value()).isEqualTo("v1");
            mockServer.verify();
        }

        @Test
        @DisplayName("should throw CacheKeyNotFoundException on 404 Not Found")
        void shouldThrowKeyNotFoundOn404() {
            mockServer.expect(requestTo("http://localhost:8082/api/v1/cache/k1"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThatThrownBy(() -> client.get(targetNode, "k1"))
                    .isInstanceOf(CacheKeyNotFoundException.class);
            mockServer.verify();
        }

        @Test
        @DisplayName("should throw NodeCommunicationException on 500 Internal Error")
        void shouldThrowNodeCommunicationOn500() {
            mockServer.expect(requestTo("http://localhost:8082/api/v1/cache/k1"))
                    .andRespond(withServerError());

            assertThatThrownBy(() -> client.get(targetNode, "k1"))
                    .isInstanceOf(NodeCommunicationException.class)
                    .hasMessageContaining("HTTP status code error");
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("put() Tests")
    class PutTests {

        @Test
        @DisplayName("should return CacheEntryResponse on success")
        void shouldReturnResponseOnSuccess() throws Exception {
            CachePutRequest putRequest = new CachePutRequest("k1", "v1", 60L);
            CacheEntryResponse mockResponse = new CacheEntryResponse(
                    "k1", "v1", Instant.now(), 0L, 60L, 60L, false
            );
            String successBody = "{\"success\":true,\"message\":\"OK\",\"data\":" +
                    objectMapper.writeValueAsString(mockResponse) + "}";

            mockServer.expect(requestTo("http://localhost:8082/api/v1/cache"))
                    .andExpect(method(org.springframework.http.HttpMethod.PUT))
                    .andExpect(content().json(objectMapper.writeValueAsString(putRequest)))
                    .andRespond(withSuccess(successBody, MediaType.APPLICATION_JSON));

            CacheEntryResponse result = client.put(targetNode, putRequest);

            assertThat(result.key()).isEqualTo("k1");
            assertThat(result.value()).isEqualTo("v1");
            mockServer.verify();
        }

        @Test
        @DisplayName("should throw CacheCapacityExceededException on 507 Insufficient Storage")
        void shouldThrowCapacityExceededOn507() {
            CachePutRequest putRequest = new CachePutRequest("k1", "v1", 60L);
            mockServer.expect(requestTo("http://localhost:8082/api/v1/cache"))
                    .andRespond(withStatus(HttpStatus.INSUFFICIENT_STORAGE));

            assertThatThrownBy(() -> client.put(targetNode, putRequest))
                    .isInstanceOf(CacheCapacityExceededException.class)
                    .hasMessageContaining("cache is full");
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("delete() Tests")
    class DeleteTests {

        @Test
        @DisplayName("should delete entry successfully")
        void shouldDeleteSuccessfully() {
            mockServer.expect(requestTo("http://localhost:8082/api/v1/cache/k1"))
                    .andExpect(method(org.springframework.http.HttpMethod.DELETE))
                    .andRespond(withSuccess());

            client.delete(targetNode, "k1");
            mockServer.verify();
        }

        @Test
        @DisplayName("should throw CacheKeyNotFoundException on 404 Not Found")
        void shouldThrowKeyNotFoundOn404() {
            mockServer.expect(requestTo("http://localhost:8082/api/v1/cache/k1"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThatThrownBy(() -> client.delete(targetNode, "k1"))
                    .isInstanceOf(CacheKeyNotFoundException.class);
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("Replication Tests")
    class ReplicationTests {

        @Test
        @DisplayName("putReplicated should send X-Replicated-From header")
        void putReplicatedShouldSendHeader() throws Exception {
            CachePutRequest putRequest = new CachePutRequest("k1", "v1", 60L);
            CacheEntryResponse mockResponse = new CacheEntryResponse(
                    "k1", "v1", Instant.now(), 0L, 60L, 60L, false
            );
            String successBody = "{\"success\":true,\"message\":\"OK\",\"data\":" +
                    objectMapper.writeValueAsString(mockResponse) + "}";

            mockServer.expect(requestTo("http://localhost:8082/api/v1/cache"))
                    .andExpect(method(org.springframework.http.HttpMethod.PUT))
                    .andExpect(header(RestClientNodeCommunicationClient.REPLICATED_FROM_HEADER, "node-1"))
                    .andRespond(withSuccess(successBody, MediaType.APPLICATION_JSON));

            CacheEntryResponse result = client.putReplicated(targetNode, putRequest, "node-1");

            assertThat(result.key()).isEqualTo("k1");
            mockServer.verify();
        }

        @Test
        @DisplayName("deleteReplicated should send X-Replicated-From header")
        void deleteReplicatedShouldSendHeader() {
            mockServer.expect(requestTo("http://localhost:8082/api/v1/cache/k1"))
                    .andExpect(method(org.springframework.http.HttpMethod.DELETE))
                    .andExpect(header(RestClientNodeCommunicationClient.REPLICATED_FROM_HEADER, "node-1"))
                    .andRespond(withSuccess());

            client.deleteReplicated(targetNode, "k1", "node-1");
            mockServer.verify();
        }
    }
}
