"""
Automated Runbook: Cache Stampede Mitigation.
Triggers when: P99 latency spikes or high rate of cache miss under intense traffic.
Steps:
1. Identify high-frequency hot keys undergoing concurrent cache miss.
2. Enable probabilistic early expiration / background key pre-warming.
3. Scale connection timeouts on Gateway to protect backend services.
4. Verify latency stabilizes below SLO threshold (P99 < 50ms).
"""
from typing import Dict, Any, List


class CacheStampedeRunbook:
    name: str = "runbook_cache_stampede"
    target_alert: str = "P99LatencySpike"

    @staticmethod
    def execute_plan(target_service: str, p99_latency_ms: float) -> List[Dict[str, Any]]:
        steps = [
            {
                "step": 1,
                "action": "query_slow_requests",
                "tool": "get_prometheus_metric",
                "params": {"query": "http_server_requests_seconds_bucket", "service": target_service},
                "critical": True,
            },
            {
                "step": 2,
                "action": "enable_stampede_protection",
                "tool": "toggle_hotkey_locking",
                "params": {"service": target_service, "lock_ttl_ms": 250},
                "requires_approval": False,
            },
            {
                "step": 3,
                "action": "verify_latency_normalization",
                "tool": "get_prometheus_metric",
                "params": {"query": "histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[1m]))"},
                "critical": True,
            },
        ]
        return steps
