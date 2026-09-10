package com.cache.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private final CacheProperties cacheProperties;

    public RestClientConfig(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    @Bean("nodeRestClient")
    public RestClient nodeRestClient() {
        CacheProperties.ClusterProperties clusterConfig = cacheProperties.getCluster();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(clusterConfig.getConnectTimeoutMs());
        factory.setReadTimeout(clusterConfig.getReadTimeoutMs());

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("X-Cache-Node-Id", cacheProperties.getNode().getId())
                .requestInterceptor(new com.cache.observability.TraceInterceptor())
                .build();
    }

    @Bean("heartbeatRestClient")
    public RestClient heartbeatRestClient() {
        CacheProperties.ClusterProperties clusterConfig = cacheProperties.getCluster();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) clusterConfig.getHeartbeatTimeoutMs());
        factory.setReadTimeout((int) clusterConfig.getHeartbeatTimeoutMs());

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("X-Cache-Node-Id", cacheProperties.getNode().getId())
                .build();
    }
}
