package com.cache.cluster.client.impl;

import com.cache.cluster.client.NodeCommunicationClient;
import com.cache.cluster.exception.NodeCommunicationException;
import com.cache.cluster.model.NodeInfo;
import com.cache.config.CacheProperties;
import com.cache.dto.request.CachePutRequest;
import com.cache.dto.response.ApiResponse;
import com.cache.dto.response.CacheEntryResponse;
import com.cache.exception.CacheCapacityExceededException;
import com.cache.exception.CacheKeyNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

@Component
public class RestClientNodeCommunicationClient implements NodeCommunicationClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientNodeCommunicationClient.class);

    public static final String FORWARDED_BY_HEADER = "X-Forwarded-By";

    public static final String REPLICATED_FROM_HEADER = "X-Replicated-From";

    private final RestClient restClient;
    private final String selfNodeId;

    public RestClientNodeCommunicationClient(@Qualifier("nodeRestClient") RestClient restClient,
                                             CacheProperties cacheProperties) {
        this.restClient = restClient;
        this.selfNodeId = cacheProperties.getNode().getId();
    }

    @Override
    public CacheEntryResponse get(NodeInfo targetNode, String key) {
        String url = targetNode.getBaseUrl() + "/api/v1/cache/" + key;
        log.debug("Outbound inter-node GET: targetNode='{}' key='{}'", targetNode.getId(), key);

        try {
            ApiResponse<CacheEntryResponse> responseEnvelope = restClient.get()
                    .uri(url)
                    .header(FORWARDED_BY_HEADER, selfNodeId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, resp) -> {
                        throw new CacheKeyNotFoundException(key);
                    })
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw new NodeCommunicationException(targetNode.getId(), url,
                                "HTTP status code error: " + resp.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<ApiResponse<CacheEntryResponse>>() {});

            if (responseEnvelope == null || responseEnvelope.data() == null) {
                throw new NodeCommunicationException(targetNode.getId(), url,
                        "Received empty response from remote node");
            }

            return responseEnvelope.data();

        } catch (CacheKeyNotFoundException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new NodeCommunicationException(targetNode.getId(), url, "Connection timed out or failed", e);
        } catch (Exception e) {
            if (e instanceof NodeCommunicationException) {
                throw (NodeCommunicationException) e;
            }
            throw new NodeCommunicationException(targetNode.getId(), url, "Unexpected exception: " + e.getMessage(), e);
        }
    }

    @Override
    public CacheEntryResponse put(NodeInfo targetNode, CachePutRequest request) {
        String url = targetNode.getBaseUrl() + "/api/v1/cache";
        log.debug("Outbound inter-node PUT: targetNode='{}' key='{}'", targetNode.getId(), request.key());

        try {
            ApiResponse<CacheEntryResponse> responseEnvelope = restClient.put()
                    .uri(url)
                    .header(FORWARDED_BY_HEADER, selfNodeId)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 507, (req, resp) -> {
                        throw new CacheCapacityExceededException("Remote node '" + targetNode.getId() + "' cache is full");
                    })
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw new NodeCommunicationException(targetNode.getId(), url,
                                "HTTP status code error: " + resp.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<ApiResponse<CacheEntryResponse>>() {});

            if (responseEnvelope == null || responseEnvelope.data() == null) {
                throw new NodeCommunicationException(targetNode.getId(), url,
                        "Received empty response from remote node");
            }

            return responseEnvelope.data();

        } catch (CacheCapacityExceededException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new NodeCommunicationException(targetNode.getId(), url, "Connection timed out or failed", e);
        } catch (Exception e) {
            if (e instanceof NodeCommunicationException) {
                throw (NodeCommunicationException) e;
            }
            throw new NodeCommunicationException(targetNode.getId(), url, "Unexpected exception: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(NodeInfo targetNode, String key) {
        String url = targetNode.getBaseUrl() + "/api/v1/cache/" + key;
        log.debug("Outbound inter-node DELETE: targetNode='{}' key='{}'", targetNode.getId(), key);

        try {
            restClient.delete()
                    .uri(url)
                    .header(FORWARDED_BY_HEADER, selfNodeId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, resp) -> {
                        throw new CacheKeyNotFoundException(key);
                    })
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw new NodeCommunicationException(targetNode.getId(), url,
                                "HTTP status code error: " + resp.getStatusCode());
                    })
                    .toBodilessEntity();

        } catch (CacheKeyNotFoundException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new NodeCommunicationException(targetNode.getId(), url, "Connection timed out or failed", e);
        } catch (Exception e) {
            if (e instanceof NodeCommunicationException) {
                throw (NodeCommunicationException) e;
            }
            throw new NodeCommunicationException(targetNode.getId(), url, "Unexpected exception: " + e.getMessage(), e);
        }
    }

    @Override
    public CacheEntryResponse putReplicated(NodeInfo targetNode, CachePutRequest request, String sourceNodeId) {
        String url = targetNode.getBaseUrl() + "/api/v1/cache";
        log.debug("Outbound inter-node replication PUT: targetNode='{}' sourceNode='{}' key='{}'",
                targetNode.getId(), sourceNodeId, request.key());

        try {
            ApiResponse<CacheEntryResponse> responseEnvelope = restClient.put()
                    .uri(url)
                    .header(REPLICATED_FROM_HEADER, sourceNodeId)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 507, (req, resp) -> {
                        throw new CacheCapacityExceededException("Remote node '" + targetNode.getId() + "' cache is full");
                    })
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw new NodeCommunicationException(targetNode.getId(), url,
                                "HTTP status code error: " + resp.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<ApiResponse<CacheEntryResponse>>() {});

            if (responseEnvelope == null || responseEnvelope.data() == null) {
                throw new NodeCommunicationException(targetNode.getId(), url,
                        "Received empty response from remote node during replication");
            }

            return responseEnvelope.data();

        } catch (CacheCapacityExceededException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new NodeCommunicationException(targetNode.getId(), url, "Connection timed out or failed during replication", e);
        } catch (Exception e) {
            if (e instanceof NodeCommunicationException) {
                throw (NodeCommunicationException) e;
            }
            throw new NodeCommunicationException(targetNode.getId(), url, "Unexpected exception: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteReplicated(NodeInfo targetNode, String key, String sourceNodeId) {
        String url = targetNode.getBaseUrl() + "/api/v1/cache/" + key;
        log.debug("Outbound inter-node replication DELETE: targetNode='{}' sourceNode='{}' key='{}'",
                targetNode.getId(), sourceNodeId, key);

        try {
            restClient.delete()
                    .uri(url)
                    .header(REPLICATED_FROM_HEADER, sourceNodeId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, resp) -> {
                        throw new CacheKeyNotFoundException(key);
                    })
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw new NodeCommunicationException(targetNode.getId(), url,
                                "HTTP status code error: " + resp.getStatusCode());
                    })
                    .toBodilessEntity();

        } catch (CacheKeyNotFoundException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new NodeCommunicationException(targetNode.getId(), url, "Connection timed out or failed during replication", e);
        } catch (Exception e) {
            if (e instanceof NodeCommunicationException) {
                throw (NodeCommunicationException) e;
            }
            throw new NodeCommunicationException(targetNode.getId(), url, "Unexpected exception: " + e.getMessage(), e);
        }
    }
}
