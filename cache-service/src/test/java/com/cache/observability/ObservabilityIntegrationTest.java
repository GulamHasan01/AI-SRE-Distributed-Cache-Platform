package com.cache.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Observability Integration Tests (Metrics & Tracing)")
class ObservabilityIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("HTTP Request should generate and return X-Trace-Id header")
    void httpRequestsContainTraceId() {
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/actuator/health",
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(response.getHeaders().containsKey("X-Trace-Id")).isTrue();
        String traceId = response.getHeaders().getFirst("X-Trace-Id");
        assertThat(traceId).isNotBlank();
    }

    @Test
    @DisplayName("Incoming X-Trace-Id header should be propagated back in the response")
    void httpRequestsPropagateIncomingTraceId() {
        String testTraceId = "test-custom-trace-id-12345";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", testTraceId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/actuator/health",
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(response.getHeaders().getFirst("X-Trace-Id")).isEqualTo(testTraceId);
    }

    @Test
    @DisplayName("Prometheus metrics endpoint should expose custom cache and cluster metrics")
    void prometheusMetricsEndpointExposesCustomMeters() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/prometheus",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();

        assertThat(body).contains("cache_hits_total");
        assertThat(body).contains("cache_misses_total");
        assertThat(body).contains("cache_evictions_total");
        assertThat(body).contains("cache_expiries_total");
        assertThat(body).contains("cache_size");

        assertThat(body).contains("cache_replication_attempts_total");
        assertThat(body).contains("cache_replication_successes_total");
        assertThat(body).contains("cache_replication_failures_total");
        assertThat(body).contains("cluster_nodes_up");
        assertThat(body).contains("cluster_size");
    }
}
