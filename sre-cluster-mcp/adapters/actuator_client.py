"""
Spring Boot Actuator adapter.
Calls /actuator/metrics, /actuator/threaddump, /actuator/health on cache nodes.
"""
import httpx
from typing import Any, Dict, List, Optional
from config import settings


def _node_url(node_id: str) -> str:
    url = settings.node_urls.get(node_id)
    if not url:
        raise ValueError(f"Unknown node_id '{node_id}'. Allowed: {list(settings.node_urls.keys())}")
    return url


async def get_health(node_id: str) -> Dict[str, Any]:
    """GET /actuator/health — Spring Boot health status."""
    base = _node_url(node_id)
    async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
        try:
            resp = await client.get(f"{base}/actuator/health")
            resp.raise_for_status()
            return {"node_id": node_id, "reachable": True, "health": resp.json()}
        except httpx.HTTPError as e:
            return {"node_id": node_id, "reachable": False, "error": str(e)}


async def get_metrics(node_id: str, metric_names: Optional[List[str]] = None) -> Dict[str, Any]:
    """
    GET /actuator/metrics and fetch specific metric values.
    If metric_names is None, returns the full list of available metrics.
    """
    base = _node_url(node_id)
    DEFAULT_METRICS = [
        "jvm.memory.used",
        "jvm.memory.max",
        "jvm.memory.committed",
        "jvm.gc.pause",
        "system.cpu.usage",
        "process.cpu.usage",
        "jvm.threads.live",
        "jvm.threads.peak",
        "cache.hits_total",
        "cache.misses_total",
        "cache.evictions_total",
        "cache.size",
    ]
    names = metric_names or DEFAULT_METRICS
    results = {}

    async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
        # First get available metric names
        try:
            avail = await client.get(f"{base}/actuator/metrics")
            avail.raise_for_status()
            available_names = avail.json().get("names", [])
            results["available_metrics"] = available_names
        except httpx.HTTPError as e:
            return {"node_id": node_id, "reachable": False, "error": str(e)}

        # Fetch each requested metric
        metric_values = {}
        for name in names:
            if name not in available_names:
                continue
            try:
                r = await client.get(f"{base}/actuator/metrics/{name}")
                if r.status_code == 200:
                    data = r.json()
                    measurements = data.get("measurements", [])
                    metric_values[name] = {
                        "description": data.get("description", ""),
                        "unit": data.get("baseUnit", ""),
                        "measurements": measurements,
                    }
            except Exception:
                pass

        results["node_id"] = node_id
        results["reachable"] = True
        results["metrics"] = metric_values

        # Compute heap usage percentage for convenience
        heap_used = None
        heap_max = None
        for m_name, m_data in metric_values.items():
            if "jvm.memory.used" in m_name and m_data.get("measurements"):
                for measurement in m_data["measurements"]:
                    if measurement.get("statistic") == "VALUE":
                        heap_used = measurement.get("value")
            if "jvm.memory.max" in m_name and m_data.get("measurements"):
                for measurement in m_data["measurements"]:
                    if measurement.get("statistic") == "VALUE":
                        heap_max = measurement.get("value")

        if heap_used and heap_max and heap_max > 0:
            results["heap_usage_pct"] = round(heap_used / heap_max * 100, 2)
        else:
            results["heap_usage_pct"] = None

    return results


async def get_thread_dump(node_id: str) -> Dict[str, Any]:
    """GET /actuator/threaddump — returns parsed thread dump."""
    base = _node_url(node_id)
    async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
        try:
            resp = await client.get(f"{base}/actuator/threaddump")
            resp.raise_for_status()
            threads = resp.json().get("threads", [])

            # Summarize thread states
            state_counts = {}
            blocked_threads = []
            for t in threads:
                state = t.get("threadState", "UNKNOWN")
                state_counts[state] = state_counts.get(state, 0) + 1
                if state in ("BLOCKED", "WAITING", "TIMED_WAITING"):
                    blocked_threads.append({
                        "name": t.get("threadName"),
                        "state": state,
                        "stack_top": t.get("stackTrace", [{}])[0] if t.get("stackTrace") else {},
                    })

            return {
                "node_id": node_id,
                "reachable": True,
                "total_threads": len(threads),
                "state_summary": state_counts,
                "blocked_threads": blocked_threads[:20],  # limit for LLM context
                "raw_thread_count": len(threads),
            }
        except httpx.HTTPError as e:
            return {"node_id": node_id, "reachable": False, "error": str(e)}


async def get_cache_stats(node_id: str) -> Dict[str, Any]:
    """GET /api/v1/cache/stats — from the cache node directly."""
    base = _node_url(node_id)
    async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
        try:
            resp = await client.get(f"{base}/api/v1/cache/stats")
            resp.raise_for_status()
            return {"node_id": node_id, "reachable": True, "stats": resp.json().get("data", {})}
        except httpx.HTTPError as e:
            return {"node_id": node_id, "reachable": False, "error": str(e)}
