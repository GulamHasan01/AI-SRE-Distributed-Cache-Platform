package com.cache.config;

import com.cache.cluster.model.NodeStatus;
import com.cache.cluster.registry.ClusterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class MetricsConfig {

    @Bean
    public PrometheusMeterRegistry prometheusMeterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    @Bean
    public org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint prometheusScrapeEndpoint(
            PrometheusMeterRegistry prometheusMeterRegistry) {
        return new org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint(prometheusMeterRegistry.getPrometheusRegistry());
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> clusterMetricsCustomizer(
            ClusterRegistry clusterRegistry,
            CacheProperties cacheProperties) {
        return registry -> {
            String nodeId = cacheProperties.getNode().getId();
            List<Tag> tags = List.of(Tag.of("node", nodeId));

            registry.gauge("cluster.nodes.up", tags, clusterRegistry, reg ->
                    (double) reg.findAll().stream()
                            .filter(node -> node.getStatus() == NodeStatus.UP)
                            .count()
            );

            registry.gauge("cluster.size", tags, clusterRegistry, reg ->
                    (double) reg.findAll().size()
            );

            registry.gauge("cache.node.last.heartbeat.seconds", tags, clusterRegistry, reg ->
                    reg.findById(nodeId)
                            .map(n -> (double) n.getLastHeartbeatAt().getEpochSecond())
                            .orElse((double) java.time.Instant.now().getEpochSecond())
            );
        };
    }
}
